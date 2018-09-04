(ns clj-karabiner.kvstore.atom
  (:require [clj-karabiner.kvstore :as kvs]
            [taoensso.nippy :as n]))


(defrecord AtomKvStore [a]

  kvs/KvStore

  (store* [this k v pre-process]
    (let [v ((or pre-process identity) v)]
      (swap! a #(assoc % k v))))

  (lookup* [this k not-found post-process]
    (-> (get @a k not-found)
        ((or post-process identity)))))


#_(n/extend-freeze AtomKvStore :atom-kvstore [x out]
                 (n/freeze-to-out! out {:a @(:a x)}))

#_(n/extend-thaw :atom-kvstore [in]
               (let [m (n/thaw-from-in! in)]
                 (map->AtomKvStore (update m :a #(atom %)))))


(defn atom-kvstore []
  (map->AtomKvStore {:a (atom {})}))
