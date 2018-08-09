(ns clj-karabiner.tree.proxy-nodes
  (:require [clj-karabiner.tree :as t]
            #_[clj-karabiner.tree.bplustree :as bt]
            [clj-karabiner.external-memory :as em])
  #_(:import [clj_karabiner.tree.bplustree B+TreeInternalNode B+TreeLeafNode]))


(defrecord B+TreeInternalNodeProxy [key]

  t/TreeModifyable

  (insert* [this k v {:keys [external-memory] :as user-data}]
    (t/insert* (em/load external-memory this) k v user-data))

  t/TreeLookupable

  (lookup* [this k {:keys [external-memory] :as user-data}]
    (t/lookup* (em/load external-memory this) k user-data))

  (lookup-range* [this k {:keys [external-memory] :as user-data}]
    (t/lookup-range* (em/load external-memory this) k user-data)))


(defrecord B+TreeLeafNodeProxy [key]

  t/TreeModifyable

  (insert* [this k v {:keys [external-memory] :as user-data}]
    (t/insert* (em/load external-memory this) k v user-data))

  t/TreeLookupable

  (lookup* [this k {:keys [external-memory] :as user-data}]
    (t/lookup* (em/load external-memory this) k user-data))

  (lookup-range* [this k {:keys [external-memory] :as user-data}]
    (t/lookup-range* (em/load external-memory this) k user-data)))


#_(def edn-readers {'clj_karabiner.tree.bplustree_proxy.B+TreeInternalNodeProxy
                  (fn [{:keys [key] :as o}]
                    (->B+TreeInternalNodeProxy key))

                  'clj_karabiner.tree.bplustree_proxy.B+TreeLeafNodeProxy
                  (fn [{:keys [key] :as o}]
                    (->B+TreeLeafNodeProxy key))})
