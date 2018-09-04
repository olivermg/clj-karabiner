(ns clj-karabiner.storage-backend.kafka
  (:require [clojure.string :as str]
            [cognitect.transit :as t]
            #_[clojure.core.async :refer [go go-loop <! >! put!] :as a]
            [clj-karabiner.storage-backend :as sb]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sg]
            [taoensso.nippy :as n])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [org.apache.kafka.clients.admin AdminClient NewTopic]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord]
           [org.apache.kafka.clients.consumer KafkaConsumer]
           [org.apache.kafka.common.serialization Serializer Deserializer]))


(defrecord TransitSerializer []

  Serializer

  (close [this])

  (configure [this configs isKey])

  (serialize [this topic data]
    (let [out (ByteArrayOutputStream. 10)
          w (t/writer out :json)]
      (t/write w data)
      (.toByteArray out))))


(defrecord TransitDeserializer []

  Deserializer

  (close [this])

  (configure [this configs isKey])

  (deserialize [this topic data]
    (let [in (ByteArrayInputStream. data)
          r (t/reader in :json)]
      (t/read r))))


(defn- topic-exists? [{:keys [topics] :as this} topic]
  (contains? @topics topic))


(defn- create-topic [{:keys [partition-count replication-factor admin topics] :as this} topic]
  (-> (.createTopics admin [(NewTopic. topic partition-count replication-factor)])
      (.all)
      (.get))
  (swap! topics #(conj % topic)))


(defn- list-topics [{:keys [topic-prefix admin] :as this}]
  (let [topics-result (.listTopics admin)
        topics (.get (.listings topics-result))]
    (->> topics
         (remove #(or (= (.isInternal %) true)
                      (not (str/starts-with? (.name %) topic-prefix))))
         (map #(.name %))
         set)))


(defrecord KafkaStorageBackend [topic-prefix bootstrap-server topic-fn key-fn value-fn partition-count replication-factor
                                topics admin producer]

  sb/LoadableStorageBackend

  (load [this]
    (println "TOPICS" @topics)
    (if (not-empty @topics)
      (let [consumer (KafkaConsumer. {"bootstrap.servers"  bootstrap-server
                                      "key.deserializer"   "clj_karabiner.storage_backend.kafka.TransitDeserializer"
                                      "value.deserializer" "clj_karabiner.storage_backend.kafka.TransitDeserializer"
                                      "group.id"           (str (java.util.UUID/randomUUID))
                                      "max.poll.records"   "500"
                                      "enable.auto.commit" "false"})]
        (doto consumer
          (.subscribe @topics)
          (.poll 0)
          (.seekToBeginning (.assignment consumer)))
        (letfn [(get-chunks [cnt]
                  (lazy-seq
                   (println "I/O" cnt)
                   (let [crs (.poll consumer 1000)]
                     (if (and crs (> (.count crs) 0))
                       (concat (mapv (fn [cr]
                                      #_{:key (.key cr)
                                       :value (.value cr)
                                       :topic (->> (.topic cr)
                                                   (drop (count topic-prefix))
                                                   (apply str))
                                       :partition (.partition cr)
                                       :offset (.offset cr)}
                                      (.value cr))
                                    crs)
                               (get-chunks (inc cnt)))
                       (do (.close consumer)
                           nil)))))]
          (get-chunks 0)))
      []))

  sb/AppendableStorageBackend

  (append [this obj]
    (let [topic (str topic-prefix (topic-fn obj))]
      (when-not (topic-exists? this topic)
        (create-topic this topic))
      (let [key (key-fn obj)
            value (value-fn obj)
            record (ProducerRecord. topic key value)
            record-meta (-> (.send producer record)
                            (.get))]
        {:partition (.partition record-meta)
         :offset (.offset record-meta)
         :timestamp (.timestamp record-meta)}))))


#_(n/extend-freeze KafkaStorageBackend :kafka-storage-backend [x out]
                 (n/freeze-to-out! out (-> (dissoc x :admin :producer :topic-fn :key-fn :value-fn)
                                           (update :topics deref))))

#_(n/extend-thaw :kafka-storage-backend [in]
               (let [{:keys [bootstrap-server topics] :as m} (n/thaw-from-in! in)
                     admin (AdminClient/create {"bootstrap.servers" bootstrap-server})
                     producer (KafkaProducer. {"bootstrap.servers" bootstrap-server
                                               "key.serializer"    "clj_karabiner.storage_backend.kafka.TransitSerializer"
                                               "value.serializer"  "clj_karabiner.storage_backend.kafka.TransitSerializer"})]
                 (map->KafkaStorageBackend (merge m {:admin admin
                                                     :producer producer
                                                     :topics (atom topics)}))))


(defn kafka-storage-backend [& {:keys [topic-prefix bootstrap-server topic-fn key-fn value-fn partition-count replication-factor]
                                :or {bootstrap-server "localhost:9092"}}]
  (let [admin (AdminClient/create {"bootstrap.servers" bootstrap-server})
        producer (KafkaProducer. {"bootstrap.servers" bootstrap-server
                                  "key.serializer"    "clj_karabiner.storage_backend.kafka.TransitSerializer"
                                  "value.serializer"  "clj_karabiner.storage_backend.kafka.TransitSerializer"})
        ksb (map->KafkaStorageBackend {:topic-prefix       (str (or topic-prefix "storage") "-")
                                       :bootstrap-server   bootstrap-server
                                       :topic-fn           (or topic-fn :topic)
                                       :key-fn             (or key-fn :key)
                                       :value-fn           (or value-fn :value)
                                       :partition-count    (or partition-count 100)
                                       :replication-factor (or partition-count 1)
                                       :admin              admin
                                       :producer           producer})]
    (assoc ksb :topics (atom (list-topics ksb)))))



;;;
;;; sample invocations
;;;

#_(let [be (kafka-storage-backend)]
  (sb/append be {:topic "supertopic555" :key "foo" :value "foov"})
  (sb/append be {:topic "supertopic555" :key "bar" :value "barv"})
  (sb/append be {:topic "supertopic666" :key "baz" :value "bazv"})
  (sb/load be))


;;; generate some data
#_(let [e-pool (set (for [ns ["artist" "record" "song"]
                        id (range 1000)]
                    (keyword ns (str "id" id))))]
  (s/def ::e   e-pool)
  (s/def ::eav (s/or :name   (s/cat :e ::e :a #{:name}   :v string?)
                     :length (s/cat :e ::e :a #{:length} :v number?)
                     :ref    (s/cat :e ::e :a #{:ref}    :v ::e)
                     :date   (s/cat :e ::e :a #{:date}   :v inst?)))

  (let [be (kafka-storage-backend
            :topic-prefix "factdb"
            :topic-fn (fn [[e a v t :as fact]]
                        (namespace e))
            :key-fn (fn [[e a v t :as fact]]
                      (name e))
            :value-fn identity)]
    (dotimes [i 3000]
      (let [g    (s/gen ::eav)
            t    (int (/ i 3))
            fact (-> (sg/generate g) vec (conj t))]
        (sb/append be fact)))))


;;; load kafka data
#_(let [be (kafka-storage-backend
          :topic-prefix "factdb"
          :topic-fn (fn [[e a v t :as fact]]
                      (namespace e))
          :key-fn (fn [[e a v t :as fact]]
                    (name e))
          :value-fn identity)]
  (count (sb/load be)))
