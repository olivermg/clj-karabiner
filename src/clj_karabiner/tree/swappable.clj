(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.kvstore :as kvs]))


(declare swappable-node)


(let [node-key-rnd (java.util.Random.)]
  (defn gen-key [tx key-prefix key-id]
    ;;; NOTE:
    ;;; - .nextLong on an already initialized Random is faster than rand-int
    ;;; - we can just take a random value as key, as the identity property is not
    ;;;   important for nodes. we don't assume that we'll generate the same node
    ;;;   more than once
    (str (or key-prefix "") ":" tx ":" (or key-id (.nextLong node-key-rnd)))))


(defrecord SwappableNode [key]

  t/ModifyableNode

  (node-insert [this tx k v {:keys [node-kvstore name] :as t}]
    (let [node (kvs/lookup node-kvstore key)
          [n1 nk n2 lnbs lv] (t/node-insert node tx k v t)
          sn1 (swappable-node node-kvstore tx :node n1 :key-prefix name)
          sn2 (and n2 (swappable-node node-kvstore tx :node n2 :key-prefix name))]
      [sn1 nk sn2 lnbs lv]))

  t/LookupableNode

  (node-lookup [this k {:keys [node-kvstore] :as t}]
    (t/node-lookup (kvs/lookup node-kvstore key) k t))

  (node-lookup-range [this k {:keys [node-kvstore] :as t}]
    (t/node-lookup-range (kvs/lookup node-kvstore key) k t)))


(defn get-node [{:keys [key] :as this} node-kvstore]
  (kvs/lookup node-kvstore key))


(defn swappable-node [node-kvstore tx & {:keys [node key-prefix key-id]}]
  (let [key (gen-key tx key-prefix key-id)]
    (when node
      (kvs/store node-kvstore key node))
    (map->SwappableNode {:key key})))
