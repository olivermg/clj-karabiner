(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.kvstore :as kvs]))


(declare swappable-node)


(defrecord SwappableNode [kvstore id]

  t/Node

  (id [this]
    (:id this))

  t/ModifyableNode

  (insert* [this k v user-data]
    (let [node (kvs/lookup kvstore id)
          [n1 nk n2 lnbs lv] (t/insert* node k v user-data)
          sn1 (swappable-node kvstore n1)
          sn2 (and n2 (swappable-node kvstore n2))]
      [sn1 nk sn2 lnbs lv]))

  t/LookupableNode

  (lookup* [this k user-data]
    (t/lookup* (kvs/lookup kvstore id) k user-data))

  (lookup-range* [this k user-data]
    (t/lookup-range* (kvs/lookup kvstore id) k user-data)))


(defn swappable-node [kvstore node]
  (let [id (t/id node)]
    (kvs/store kvstore id node)
    (map->SwappableNode {:kvstore kvstore
                         :id      id})))
