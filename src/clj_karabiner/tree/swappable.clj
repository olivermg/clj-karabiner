(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]))


(defrecord SwappableNode [node]

  t/ModifyableNode

  (insert* [this k v user-data]
    (t/insert* node k v user-data))

  t/LookupableNode

  (lookup* [this k user-data]
    (t/lookup* node k user-data))

  (lookup-range* [this k user-data]
    (t/lookup-range* node k user-data)))


(defn swappable-node [node]
  (map->SwappableNode {:node node}))
