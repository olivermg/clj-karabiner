(ns clj-karabiner.kvstore)


(defprotocol KvStore
  (store* [this k v pre-process])
  (lookup* [this k not-found post-process]))


(defn store [this k v & {:keys [pre-process]}]
  (store* this k v pre-process))


(defn lookup [this k & {:keys [not-found post-process]}]
  (lookup* this k not-found post-process))
