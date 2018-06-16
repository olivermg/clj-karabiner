(ns clj-karabiner.external-storage.memory
  (:require [clj-karabiner.external-storage :as es]))


(defrecord MemoryBackend [storage]

  es/ExternalStorage

  (load-data [this k]
    (get @storage k))

  (save-data [this k data]
    (swap! storage #(assoc % k data))))


(defn memory-backend []
  (->MemoryBackend (atom {})))
