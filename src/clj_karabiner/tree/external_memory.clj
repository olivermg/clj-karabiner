(ns clj-karabiner.tree.external-memory
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree :as bt]
            [clj-karabiner.external-memory :as em])
  (:import [clj_karabiner.tree.bplustree B+TreeInternalNode B+TreeLeafNode]))


(defrecord B+TreeInternalNodeProxy [key storage]

  t/TreeModifyable

  (insert* [this k v user-data]
    #_(t/insert* (es/load this) k v user-data))

  t/TreeLookupable

  (lookup* [this k user-data]
    #_(t/lookup* (t/load this) k user-data))

  (lookup-range* [this k user-data]
    #_(t/lookup-range* (t/load this) k user-data)))


(defrecord B+TreeLeafNodeProxy [])


(defn make-serializers []
  {B+TreeInternalNode
   (letfn [(data [o]
             (select-keys o #{:b :size :ks :vs}))]
     {:key-fn  (fn [o]
                 (hash (data o)))
      :data-fn data})

   B+TreeLeafNode
   (letfn [(data [o]
             (select-keys o #{:b :size :m}))]
     {:key-fn (fn [o]
                (hash (data o)))
      :data-fn data})})

(defn make-deserializers []
  {B+TreeInternalNodeProxy
   {:key-fn :key
    :real-obj-fn (fn [o]
                   (bt/->B+TreeInternalNode (:b o) (:size o) (:ks o) (:vs o)))}

   B+TreeLeafNodeProxy
   {:key-fn :key
    :real-obj-fn (fn [o]
                   (bt/->B+TreeLeafNode (:b o) (:size o) (:m o)))}})

(def proxy-creators {B+TreeInternalNode
                     (fn [o]
                       (->B+TreeInternalNodeProxy (hash (select-keys o #{:b :size :ks :vs}))))

                     B+TreeLeafNode
                     (fn [o]
                       (->B+TreeLeafNodeProxy (hash (select-keys o #{:b :size :m}))))})
