(ns clj-karabiner.kvstore.mutable-cache
  (:require [clj-karabiner.kvstore :as kvs]
            [clj-karabiner.dedup-list :as ddl]))


(defrecord MutableCachingKvStore [l m]

  kvs/KvStore

  (store [this k v]
    (swap! l #(conj % k))
    (swap! m #(select-keys (assoc % k v) @l)))

  (lookup* [this k not-found]
    (let [v (get @m k ::not-found)]
      (if-not (= v ::not-found)
        (do (swap! l #(conj % k))
            v)
        not-found))))


(defn mutable-caching-kvstore [size]
  (map->MutableCachingKvStore {:l (atom (ddl/dedup-list :maxlen size))
                               :m (atom {})}))
