(ns clj-karabiner.storage-backend.kafka
  (:require #_[clojure.core.async :refer [go go-loop <! >! put!] :as a]
            [clj-karabiner.storage-backend :as sb])
  (:import [org.apache.kafka.clients.admin AdminClient]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord]
           [org.apache.kafka.clients.consumer KafkaConsumer]))


(defrecord KafkaStorageBackend [topic-fn partition-fn
                                admin producer]

  sb/LoadableStorageBackend

  (load [this]
    )

  sb/AppendableStorageBackend

  (append [this obj]
    ))


(defn kafka-storage-backend [bootstrap-server topic-fn partition-fn]
  (map->KafkaStorageBackend {:topic-fn topic-fn
                             :partition-fn partition-fn
                             :admin (AdminClient/create {"bootstrap.servers" bootstrap-server})
                             :producer (KafkaProducer. {"boostrap.servers" bootstrap-server
                                                        "key.serializer" "org.apache.kafka.common.serialization.StringSerializer"
                                                        "value.serializer" "org.apache.kafka.common.serialization.StringSerializer"})}))
