(ns clj-karabiner.storage-backend.kafka
  (:require #_[clojure.core.async :refer [go go-loop <! >! put!] :as a]
            [clj-karabiner.storage-backend :as sb])
  (:import [org.apache.kafka.clients.admin AdminClient NewTopic]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord]
           [org.apache.kafka.clients.consumer KafkaConsumer]))


(defn- topic-exists? [{:keys [topics] :as this} topic]
  (contains? @topics topic))


(defn- create-topic [{:keys [partition-count replication-factor admin topics] :as this} topic]
  (-> (.createTopics admin [(NewTopic. topic partition-count replication-factor)])
      (.all)
      (.get))
  (swap! topics #(conj % topic)))


(defn- list-topics [{:keys [admin] :as this}]
  (let [topics-result (.listTopics admin)
        topics (.get (.listings topics-result))]
    (->> (remove #(= (.isInternal %) true) topics)
         (map #(.name %))
         set)))


(defrecord KafkaStorageBackend [bootstrap-server topic-fn key-fn value-fn partition-count replication-factor
                                topics admin producer]

  sb/LoadableStorageBackend

  (load [this]
    (let [consumer (KafkaConsumer. {"bootstrap.servers"  bootstrap-server
                                    "key.deserializer"   "org.apache.kafka.common.serialization.StringDeserializer"
                                    "value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"
                                    "group.id"           (str (java.util.UUID/randomUUID))
                                    "enable.auto.commit" "false"})]
      (doto consumer
        (.subscribe @topics)
        (.poll 0)
        (.seekToBeginning (.assignment consumer)))
      (let [crs (->> (.poll consumer 100)
                     (map (fn [cr]
                            {:key (.key cr)
                             :value (.value cr)
                             :topic (.topic cr)
                             :partition (.partition cr)
                             :offset (.offset cr)})))]
        (.close consumer)
        crs)))

  sb/AppendableStorageBackend

  (append [this obj]
    (let [topic (topic-fn obj)]
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


(defn kafka-storage-backend [& {:keys [bootstrap-server topic-fn key-fn value-fn partition-count replication-factor]
                                :or {bootstrap-server "localhost:9092"}}]
  (let [admin (AdminClient/create {"bootstrap.servers" bootstrap-server})
        producer (KafkaProducer. {"bootstrap.servers" bootstrap-server
                                  "key.serializer"    "org.apache.kafka.common.serialization.StringSerializer"
                                  "value.serializer"  "org.apache.kafka.common.serialization.StringSerializer"})
        ksb (map->KafkaStorageBackend {:bootstrap-server   bootstrap-server
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
