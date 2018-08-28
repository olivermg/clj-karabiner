(ns clj-karabiner.cache
  (:refer-clojure :rename {keys keys-clj}))


(defprotocol Caching
  (store [this k v])
  (lookup* [this k not-found])
  (keys [this]))


(defn lookup [this k & [not-found]]
  (lookup* this k not-found))
