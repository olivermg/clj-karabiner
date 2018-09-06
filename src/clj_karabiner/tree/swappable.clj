(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.kvstore :as kvs]))


(declare swappable-node)


(let [node-id-rnd (java.util.Random.)]
  (defn new-nodeid []
    ;;; NOTE:
    ;;; - .nextLong on an already initialized Random is faster than rand-int
    ;;; - we can just take a random value as node id, as the identity property is not
    ;;;   important for nodes. we don't assume that we'll generate the same node
    ;;;   more than once
    (.nextLong node-id-rnd)))


(defrecord SwappableNode [id]

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
  (let [id (new-nodeid)]
    (kvs/store node-kvstore id node)
    (map->SwappableNode {:id id})))
