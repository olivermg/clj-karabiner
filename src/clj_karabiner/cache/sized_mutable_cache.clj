(ns clj-karabiner.cache.sized-mutable-cache
  (:require [clj-karabiner.cache :as c]
            [clj-karabiner.dedup-list :as ddl]))


(defrecord SizedMutableCache [l m]

  c/Caching

  (store [this k v]
    (swap! l #(conj % k))
    (swap! m #(select-keys (assoc % k v) @l)))

  (lookup* [this k not-found]
    (let [v (get m k ::not-found)]
      (if-not (= v ::not-found)
        (do (swap! l #(conj % k))
            v)
        not-found)))

  (keys [this]
    @l))


(defn sized-mutable-cache [size]
  (map->SizedMutableCache {:l (atom (ddl/dedup-list :maxlen size))
                           :m (atom {})}))
