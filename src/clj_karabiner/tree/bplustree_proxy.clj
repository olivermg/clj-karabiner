(ns clj-karabiner.tree.bplustree-proxy
  (:require [clj-karabiner.tree :as t]
            #_[clj-karabiner.tree.bplustree :as bt]
            [clj-karabiner.external-memory :as em])
  #_(:import [clj_karabiner.tree.bplustree B+TreeInternalNode B+TreeLeafNode]))


(defrecord B+TreeInternalNodeProxy [key]

  t/TreeModifyable

  (insert* [this k v {:keys [extmem] :as user-data}]
    (t/insert* (em/load extmem this) k v user-data))

  t/TreeLookupable

  (lookup* [this k {:keys [extmem] :as user-data}]
    (t/lookup* (em/load extmem this) k user-data))

  (lookup-range* [this k {:keys [extmem] :as user-data}]
    (t/lookup-range* (em/load extmem this) k user-data)))


(defrecord B+TreeLeafNodeProxy [key]

  t/TreeModifyable

  (insert* [this k v {:keys [extmem] :as user-data}]
    (t/insert* (em/load extmem this) k v user-data))

  t/TreeLookupable

  (lookup* [this k {:keys [extmem] :as user-data}]
    (t/lookup* (em/load extmem this) k user-data))

  (lookup-range* [this k {:keys [extmem] :as user-data}]
    (t/lookup-range* (em/load extmem this) k user-data)))


(def edn-readers {'clj_karabiner.tree.bplustree_proxy.B+TreeInternalNodeProxy
                  (fn [{:keys [key] :as o}]
                    (->B+TreeInternalNodeProxy key))

                  'clj_karabiner.tree.bplustree_proxy.B+TreeLeafNodeProxy
                  (fn [{:keys [key] :as o}]
                    (->B+TreeLeafNodeProxy key))})
