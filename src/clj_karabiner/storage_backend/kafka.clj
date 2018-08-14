(ns clj-karabiner.storage-backend.kafka
  (:require #_[clojure.core.async :refer [go go-loop <! >! put!] :as a]
            [clj-karabiner.storage-backend :as sb])
  (:import [org.apache.kafka.clients.admin AdminClient NewTopic]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord]
           [org.apache.kafka.clients.consumer KafkaConsumer]))


(defrecord KafkaStorageBackend [topic-fn partition-fn partition-count replication-factor
                                topics admin producer]

  sb/LoadableStorageBackend

  (load [this]
    )

  sb/AppendableStorageBackend

  (append [this obj]
    ))


(defn- topic-exists? [{:keys [topics] :as this} topic]
  (contains? topics topic))


(defn- create-topic [{:keys [partition-count replication-factor admin] :as this} topic]
  (-> (.createTopics admin [(NewTopic. topic partition-count replication-factor)])
      (.all)
      (.get)))


(defn- list-topics [{:keys [admin] :as this}]
  (let [topics-result (.listTopics admin)
        topics (.get (.listings topics-result))]
    (->> (remove #(= (.isInternal %) true) topics)
         (map #(.name %))
         set)))


(defn kafka-storage-backend [& {:keys [bootstrap-server topic-fn partition-fn partition-count replication-factor]
                                :or {bootstrap-server "localhost:9092"}}]
  (let [admin (AdminClient/create {"bootstrap.servers" bootstrap-server})
        producer (KafkaProducer. {"bootstrap.servers" bootstrap-server
                                  "key.serializer"    "org.apache.kafka.common.serialization.StringSerializer"
                                  "value.serializer"  "org.apache.kafka.common.serialization.StringSerializer"})
        ksb (map->KafkaStorageBackend {:topic-fn           (or topic-fn :topic)
                                       :partition-fn       (or partition-fn :partition)
                                       :partition-count    (or partition-count 100)
                                       :replication-factor (or partition-count 1)
                                       :admin              admin
                                       :producer           producer})]
    (assoc ksb :topics (list-topics ksb))))
