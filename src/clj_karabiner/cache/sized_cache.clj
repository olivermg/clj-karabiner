(ns clj-karabiner.cache.sized-cache
  (:require [clj-karabiner.cache :as c]
            [clj-karabiner.dedup-list :as ddl]))


(defrecord SizedCache [l m]

  c/Caching

  (store [this k v]
    (let [nl (conj l k)]
      (SizedCache. nl (select-keys (assoc m k v) nl))))

  (lookup [this k]
    (let [v (get m k)]
      ;;; TODO: do we want the user to be aware of that lookup
      ;;;   changed state? or do we rather want to handle this
      ;;;   internally via mutable state (atom)?
      [v (if v
           (SizedCache. (conj l k) m)
           this)]))

  (keys [this]
    l))


(defn sized-cache [size]
  (->SizedCache (ddl/dedup-list :maxlen size) {}))
