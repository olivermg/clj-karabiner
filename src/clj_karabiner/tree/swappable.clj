(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.kvstore :as kvs]))


(declare swappable-node)


(let [node-key-rnd (java.util.Random.)]
  (defn new-key [node tx]
    ;;; NOTE:
    ;;; - .nextLong on an already initialized Random is faster than rand-int
    ;;; - we can just take a random value as key, as the identity property is not
    ;;;   important for nodes. we don't assume that we'll generate the same node
    ;;;   more than once
    (str tx ":" (-> node class (.getSimpleName)) ":" (.nextLong node-key-rnd))))


(defrecord SwappableNode [key]

  t/ModifyableNode

  (insert* [this tx k v {:keys [node-kvstore] :as t}]
    (let [node (kvs/lookup node-kvstore key)
          [n1 nk n2 lnbs lv] (t/insert node tx k v :tree t)
          sn1 (swappable-node node-kvstore tx n1)
          sn2 (and n2 (swappable-node node-kvstore tx n2))]
      [sn1 nk sn2 lnbs lv]))

  t/LookupableNode

  (lookup* [this k {:keys [node-kvstore] :as t}]
    (t/lookup (kvs/lookup node-kvstore key) k :tree t))

  (lookup-range* [this k {:keys [node-kvstore] :as t}]
    (t/lookup-range (kvs/lookup node-kvstore key) k :tree t)))


(defn swappable-node [node-kvstore tx node]
  (let [key (new-key node tx)]
    (kvs/store node-kvstore key node)
    (map->SwappableNode {:key key})))
