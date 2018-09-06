(ns clj-karabiner.tree
  (:refer-clojure :rename {load load-clj}))


(defprotocol ModifyableNode
  (insert* [this tx k v tree]))

(defn insert [this tx k v & {:keys [tree]}]
  (insert* this tx k v (or tree this)))


(defprotocol LookupableNode
  (lookup* [this k tree])
  (lookup-range* [this k tree]))

(defn lookup [this k & {:keys [tree]}]
  (lookup* this k (or tree this)))

(defn lookup-range [this k & {:keys [tree]}]
  (lookup-range* this k (or tree this)))
