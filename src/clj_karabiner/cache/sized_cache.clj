(ns clj-karabiner.cache.sized-cache
  (:require [clj-karabiner.cache :as c]))


(deftype DedupList [l s maxlen curlen]

  clojure.lang.IPersistentCollection

  (count [this]
    (.count l))

  (empty [this]
    (DedupList. '() #{} nil 0))

  (equiv [this o]
    (.equiv l (:l o)))

  (cons [this o]
    (if (contains? s o)
      (DedupList. (.cons (remove #(= % o) l) o) s maxlen curlen)
      (let [ncurlen (inc curlen)]
        (if-not maxlen
          (DedupList. (.cons l o) (conj s o) maxlen ncurlen)
          (if (<= ncurlen maxlen)
            (DedupList. (.cons l o) (conj s o) maxlen ncurlen)
            (DedupList. (take maxlen (.cons l o))
                        (-> (conj s o)
                            (disj (last l)))
                        maxlen
                        curlen))))))

  clojure.lang.Seqable

  (seq [this]
    (.seq l))

  clojure.lang.ISeq

  (first [this]
    (.first l))

  (next [this]
    (.next l))

  (more [this]
    (.more l)))


(defn dedup-list [& {:keys [maxlen]}]
  (->DedupList '() #{} maxlen 0))


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
  (->SizedCache (dedup-list :maxlen size) {}))
