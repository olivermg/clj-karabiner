(ns clj-karabiner.kvstore.atom
  (:require [clj-karabiner.kvstore :as kvs]
            [taoensso.nippy :as n]))


(defrecord AtomKvStore [a]

  kvs/KvStore

  (store [this k v]
    (swap! a #(assoc % k v)))

  (lookup* [this k not-found]
    (get @a k not-found)))


#_(n/extend-freeze AtomKvStore :atom-kvstore [x out]
                 (n/freeze-to-out! out {:a @(:a x)}))

#_(n/extend-thaw :atom-kvstore [in]
               (let [m (n/thaw-from-in! in)]
                 (map->AtomKvStore (update m :a #(atom %)))))


(defn atom-kvstore []
  (map->AtomKvStore {:a (atom {})}))


(defn start [this]
  this)


(defn stop [this]
  this)
