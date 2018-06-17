(ns clj-karabiner.external-storage.memory
  (:require [clj-karabiner.external-storage :as es]))


(defrecord MemoryData [storage key]

  es/StoredData

  (load [this]
    (es/load-data storage key)))


(defrecord MemoryStorage [storage]

  es/ExternalStorage

  (load-data [this k]
    (get @storage k))

  (save-data [this o]
    (let [k (es/key o)
          d (es/data o)]
      (swap! storage #(assoc % k d))
      (->MemoryData this k))))


(defn memory-storage []
  (->MemoryStorage (atom {})))
