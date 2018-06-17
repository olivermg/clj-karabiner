(ns clj-karabiner.tree.stored-node
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.external-storage :as es]))


(defrecord StoredNode [key storage]

  t/TreeModifyable

  (insert* [this k v user-data]
    (t/insert* (es/load this) k v user-data))

  t/TreeLookupable

  (lookup* [this k user-data]
    (t/lookup* (t/load this) k user-data))

  (lookup-range* [this k user-data]
    (t/lookup-range* (t/load this) k user-data))

  es/StorageBacked

  (load [this]
    (t/load-data storage key))

  (save [this]
    this)

  (saved-representation [this]
    this))
