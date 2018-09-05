(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.kvstore :as kvs]))


(declare swappable-node)


(defrecord SwappableNode [id]

  t/Node

  (id [this]
    (:id this))

  t/SerializableNode

  (serialize* [this t]
    this)

  t/DeserializableNode

  (deserialize* [this t]
    this)

  t/ModifyableNode

  (insert* [this k v {:keys [node-kvstore] :as t}]
    (let [node (kvs/lookup node-kvstore id)
          [n1 nk n2 lnbs lv] (t/insert node k v :tree t)
          sn1 (swappable-node node-kvstore n1)
          sn2 (and n2 (swappable-node node-kvstore n2))]
      [sn1 nk sn2 lnbs lv]))

  t/LookupableNode

  (lookup* [this k {:keys [node-kvstore] :as t}]
    (t/lookup (kvs/lookup node-kvstore id) k :tree t))

  (lookup-range* [this k {:keys [node-kvstore] :as t}]
    (t/lookup-range (kvs/lookup node-kvstore id) k :tree t)))


(defn swappable-node [node-kvstore node]
  (let [id (t/id node)]
    (kvs/store node-kvstore id node)
    (map->SwappableNode {:id id})))
