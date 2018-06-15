(ns clj-karabiner.tree
  (:refer-clojure :rename {load load-clj}))


(defprotocol ExternalStorage
  (load-data [this k])
  (save-data [this k data]))

(defprotocol StorageBacked
  (load [this])
  (save [this]))


(defprotocol TreeModifyable
  (insert* [this k v user-data]))

(defn insert [this k v]
  (insert* this k v nil))


(defprotocol TreeLookupable
  (lookup* [this k user-data])
  (lookup-range* [this k user-data]))

(defn lookup [this k]
  (lookup* this k nil))

(defn lookup-range [this k]
  (lookup-range* this k nil))
