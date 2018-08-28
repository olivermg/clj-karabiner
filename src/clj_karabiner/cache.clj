(ns clj-karabiner.cache
  (:refer-clojure :rename {keys keys-clj}))


(defprotocol Caching
  (store [this k v])
  (lookup [this k])
  (keys [this]))
