(ns clj-karabiner.tree
  (:refer-clojure :rename {load load-clj}))


(defprotocol ModifyableTree
  (insert* [this tx k v]))

(defn insert [this tx k v]
  (insert* this tx k v))

(defn insert-tx [this tx kv-map]
  (reduce (fn [t [k v]]
            (insert t tx k v))
          this
          kv-map))


(defprotocol LookupableTree
  (lookup* [this k])
  (lookup-range* [this k]))

(defn lookup [this k]
  (lookup* this k))

(defn lookup-range [this k]
  (lookup-range* this k))


(defprotocol SnapshotableTree
  (snapshot [this]))


(defprotocol ModifyableNode
  (node-insert [this tx k v tree]))


(defprotocol LookupableNode
  (node-lookup [this k tree])
  (node-lookup-range [this k tree]))
