(ns clj-karabiner.kvstore.chain
  (:require [clojure.tools.logging :refer [trace debug info warn error fatal]]
            [clj-karabiner.kvstore :as kvs]))


(defrecord KvStoreChain [kvstores]

  kvs/KvStore

  (store [this k v]
    ;;; TODO: do this in parallel?
    (dorun (map #(kvs/store % k v) kvstores)))

  (lookup* [this k not-found]
    (loop [[store & stores] kvstores
           i 0]
      (if store
        (let [v (kvs/lookup store k ::not-found)]
          (if-not (= v ::not-found)
            (do #_(trace "HIT in store" i "for key" k)
                v)
            (do #_(trace "MISS in store" i "for key" k)
                (recur stores (inc i)))))
        not-found))))


(defn kvstore-chain [& kvstores]
  (map->KvStoreChain {:kvstores kvstores}))


(defn start [this]
  this)


(defn stop [this]
  this)
