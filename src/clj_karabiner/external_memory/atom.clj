(ns clj-karabiner.external-memory.atom
  (:require [clojure.edn :as edn]
            [clj-karabiner.external-memory :as em]))


#_(defrecord MemoryData [storage key]

  em/StoredData

  (load [this]
    (es/load-data storage key)))


(defrecord AtomStorage [a readers]

  em/MemoryStorage

  (load* [this k]
    (->> (get @a k)
         (edn/read-string readers)))

  (save* [this k d]
    (swap! a #(assoc % k (pr-str d)))))


(defn atom-storage [readers]
  (->AtomStorage (atom {}) {:readers readers}))
