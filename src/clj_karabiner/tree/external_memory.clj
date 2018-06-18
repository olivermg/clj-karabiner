(ns clj-karabiner.tree.external-memory
  (:require [clj-karabiner.tree :as t]
            #_[clj-karabiner.tree.bplustree :as bt]
            [clj-karabiner.external-memory :as em])
  (:import [clj_karabiner.tree.bplustree B+TreeInternalNode B+TreeLeafNode]))


(defrecord B+TreeInternalNodeProxy [key storage]

  t/TreeModifyable

  (insert* [this k v user-data]
    (t/insert* (es/load this) k v user-data))

  t/TreeLookupable

  (lookup* [this k user-data]
    (t/lookup* (t/load this) k user-data))

  (lookup-range* [this k user-data]
    (t/lookup-range* (t/load this) k user-data)))


(defrecord B+TreeLeafNodeProxy [])


(def serializers {B+TreeInternalNode
                  {:key-fn nil
                   :data-fn nil}

                  B+TreeLeafNode
                  {:key-fn nil
                   :data-fn nil}})

(def deserializers {B+TreeInternalNodeProxy
                    {:key-fn nil
                     :real-obj-fn nil}

                    B+TreeLeafNodeProxy
                    {:key-fn nil
                     :real-obj-fn nil}})

(def proxy-creators {B+TreeInternalNode
                     nil

                     B+TreeLeafNode
                     nil})
