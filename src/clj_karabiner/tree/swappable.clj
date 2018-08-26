(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]))


(declare swappable-node)


(defrecord SwappableNode [node-id node]

  t/Node

  (id [this]
    node-id)

  t/ModifyableNode

  (insert* [this k v user-data]
    (let [[n1 nk n2 lnbs lv] (t/insert* node k v user-data)
          sn1 (swappable-node n1)
          sn2 (and n2 (swappable-node n2))]
      #_(println "SWAPPABLE INSERT" (:id n1) (:id n2))
      [sn1 nk sn2 lnbs lv]))

  t/LookupableNode

  (lookup* [this k user-data]
    (t/lookup* node k user-data))

  (lookup-range* [this k user-data]
    (t/lookup-range* node k user-data)))


(defn swappable-node [node]
  (map->SwappableNode {:node-id (t/id node)
                       :node node}))
