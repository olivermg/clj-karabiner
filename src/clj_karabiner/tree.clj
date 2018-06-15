(ns clj-karabiner.tree
  (:refer-clojure :rename {load load-clj}))


(defprotocol StorageBacked
  (load [this])
  (save [this]))


(defprotocol TreeModifyable
  (insert [this k v]))

(defprotocol TreeLookupable
  (lookup [this k])
  (lookup-range* [this k]))

(defn lookup-range
  ([this k] (lookup-range* this k))
  ([this]   (lookup-range* this nil)))
