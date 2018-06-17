(ns clj-karabiner.external-storage.memory
  (:require [clj-karabiner.external-storage :as es]))


(defrecord MemoryData [storage key]

  es/StoredData

  (load [this]
    (es/load-data storage key)))


(defrecord MemoryStorage [memory]

  es/ExternalStorage

  (load-data [this k]
    (get @memory k))

  (save-data [this o]
    (let [k (es/key o)
          d (es/data o)]
      (swap! memory #(assoc % k d)))))


(defn memory-storage []
  (->MemoryStorage (atom {})))
