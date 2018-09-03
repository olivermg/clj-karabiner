(ns clj-karabiner.kvstore.atom
  (:require [clj-karabiner.kvstore :as kvs]
            [taoensso.nippy :as n]))


(defrecord AtomKvStore [a]

  kvs/KvStore

  (store [this k v]
    (swap! a #(assoc % k v)))

  (lookup* [this k not-found]
    (get @a k not-found)))


(defrecord AtomKvStore* [a])  ;; NOTE: helper record for nippy serialization

(n/extend-freeze AtomKvStore :atom-kvstore [x out]
                 (n/freeze-to-out! out (->AtomKvStore* @(:a x))))

(n/extend-thaw :atom-kvstore [in]
               (let [akvs (n/thaw-from-in! in)]
                 (->AtomKvStore (atom (:a akvs)))))


(defn atom-kvstore []
  (map->AtomKvStore {:a (atom {})}))
