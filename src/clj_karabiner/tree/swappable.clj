(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.kvstore :as kvs]))


(declare swappable-node)


(let [node-key-rnd (java.util.Random.)]
  (defn new-key [node tx key-prefix key-id]
    ;;; NOTE:
    ;;; - .nextLong on an already initialized Random is faster than rand-int
    ;;; - we can just take a random value as key, as the identity property is not
    ;;;   important for nodes. we don't assume that we'll generate the same node
    ;;;   more than once
    (str (or key-prefix "") ":" tx ":" (or key-id (.nextLong node-key-rnd)))))


(defrecord SwappableNode [key]

  t/ModifyableNode

  (insert* [this tx k v {:keys [node-kvstore name] :as t}]
    (let [node (kvs/lookup node-kvstore key)
          [n1 nk n2 lnbs lv] (t/insert node tx k v :tree t)
          sn1 (swappable-node n1 node-kvstore tx :key-prefix name)
          sn2 (and n2 (swappable-node n2 node-kvstore tx :key-prefix name))]
      [sn1 nk sn2 lnbs lv]))

  t/LookupableNode

  (lookup* [this k {:keys [node-kvstore] :as t}]
    (t/lookup (kvs/lookup node-kvstore key) k :tree t))

  (lookup-range* [this k {:keys [node-kvstore] :as t}]
    (t/lookup-range (kvs/lookup node-kvstore key) k :tree t)))


(defn get-node [{:keys [key] :as this} node-kvstore]
  (kvs/lookup node-kvstore key))


(defn swappable-node [node node-kvstore tx & {:keys [key-prefix key-id]}]
  (let [key (new-key node tx key-prefix key-id)]
    (kvs/store node-kvstore key node)
    (map->SwappableNode {:key key})))
