(ns clj-karabiner.kvstore.chain
  (:require [clj-karabiner.kvstore :as kvs]))


(defrecord KvStoreChain [kvstores]

  kvs/KvStore

  (store [this k v]
    ;;; TODO: do this in parallel?
    (dorun (map #(kvs/store % k v) kvstores)))

  (lookup* [this k not-found]
    (loop [[store & stores] kvstores]
      (if store
        (let [v (kvs/lookup store k ::not-found)]
          (if-not (= v ::not-found)
            v
            (recur stores)))
        not-found))))


(defn kvstore-chain [& kvstores]
  (map->KvStoreChain {:kvstores kvstores}))
