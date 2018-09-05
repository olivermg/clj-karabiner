(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.kvstore :as kvs]))


(declare swappable-node)


(defrecord SwappableNode [kvstore id]

  t/Node

  (id [this]
    (:id this))

  t/ModifyableNode

  (insert* [this k v t]
    (let [node (kvs/lookup kvstore id)
          [n1 nk n2 lnbs lv] (t/insert* node k v t)
          sn1 (swappable-node kvstore n1)
          sn2 (and n2 (swappable-node kvstore n2))]
      [sn1 nk sn2 lnbs lv]))

  t/LookupableNode

  (lookup* [this k t]
    (t/lookup* (kvs/lookup kvstore id) k t))

  (lookup-range* [this k t]
    (t/lookup-range* (kvs/lookup kvstore id) k t)))


(defn swappable-node [kvstore node]
  (let [id (t/id node)]
    (kvs/store kvstore id node)
    (map->SwappableNode {:kvstore kvstore
                         :id      id})))
