(ns clj-karabiner.kvstore)


(defprotocol KvStore
  (store [this k v])
  (lookup* [this k not-found]))


(defn lookup [this k & [not-found]]
  (lookup* this k not-found))
