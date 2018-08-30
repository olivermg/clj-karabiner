(ns clj-karabiner.dedup-list)


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
                            (disj (last l)))  ;; TODO: optimize runtime
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
