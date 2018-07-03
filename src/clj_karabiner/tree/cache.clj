(ns clj-karabiner.tree.cache)


(deftype DedupList [l s]

  clojure.lang.IPersistentCollection

  (count [this]
    (.count l))

  (empty [this]
    (DedupList. '() #{}))

  (equiv [this o]
    (.equiv l (:l o)))

  (cons [this o]
    (if (contains? s o)
      (DedupList. (.cons (remove #(= % o) l) o) s)
      (DedupList. (.cons l o) (conj s o))))

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


(defn dedup-list []
  (->DedupList '() #{}))


(defprotocol Caching
  (store [this k v])
  (lookup [this k]))


(defrecord SizedCache [maxsize cursize l m]

  Caching

  (store [this k v]
    (if (contains? m k)
      (SizedCache. maxsize
                   cursize
                   (take maxsize (conj l k))
                   (assoc m k v))
      (let [ncursize (inc cursize)
            nm (assoc m k v)]
        (SizedCache. maxsize
                     (min maxsize ncursize)
                     (take maxsize (conj l k))
                     (if (> ncursize maxsize)
                       (dissoc nm (last l))
                       nm)))))

  (lookup [this k]
    (get m k)))


(defn sized-cache [size]
  (->SizedCache size 0 (dedup-list) {}))
