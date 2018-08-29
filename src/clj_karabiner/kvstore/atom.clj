(ns clj-karabiner.kvstore.atom
  (:require [clj-karabiner.kvstore :as kvs]))


(defrecord AtomKvStore [a]

  kvs/KvStore

  (store [this k v]
    (swap! a #(assoc % k v)))

  (lookup* [this k not-found]
    (get @a k not-found)))


(defn atom-kvstore []
  (map->AtomKvStore {:a (atom {})}))
