(ns clj-karabiner.external-memory.atom
  (:require [clojure.edn :as edn]
            [clj-karabiner.external-memory :as em]))


#_(defrecord MemoryData [storage key]

  em/StoredData

  (load [this]
    (es/load-data storage key)))


(defrecord AtomStorage [a]

  em/MemoryStorage

  (load* [this k]
    (->> (get @a k)
         (edn/read-string)))

  (save* [this k d]
    (swap! a #(assoc % k (pr-str d)))))


(defn atom-storage []
  (->AtomStorage (atom {})))
