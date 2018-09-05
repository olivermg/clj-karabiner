(ns clj-karabiner.tree
  (:refer-clojure :rename {load load-clj}))


(defprotocol Node
  (id [this]))


(defprotocol SerializableNode
  (serialize* [this t]))

(defn serialize [this & {:keys [tree]}]
  (serialize* this (or tree this)))


(defprotocol DeserializableNode
  (deserialize* [this t]))

(defn deserialize [this & {:keys [tree]}]
  (deserialize* this (or tree this)))


(defprotocol ModifyableNode
  (insert* [this k v t]))

(defn insert [this k v & {:keys [tree]}]
  (insert* this k v (or tree this)))


(defprotocol LookupableNode
  (lookup* [this k t])
  (lookup-range* [this k t]))

(defn lookup [this k & {:keys [tree]}]
  (lookup* this k (or tree this)))

(defn lookup-range [this k & {:keys [tree]}]
  (lookup-range* this k (or tree this)))
