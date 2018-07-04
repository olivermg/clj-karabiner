(ns clj-karabiner.tree.cache)


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
    [(get m k)
     (SizedCache. maxsize
                  cursize
                  (conj l k)  ;; dedup-list already removes duplicate k and puts it to front of list
                  m)]))


(defn sized-cache [size]
  (->SizedCache size 0 (dedup-list) {}))
