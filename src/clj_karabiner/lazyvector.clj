(ns clj-karabiner.lazyvector)


(declare new-lazyvector)

(deftype LazyVector [contents]

  clojure.lang.IPersistentVector
  (length [this]
    (.length @contents))
  (assocN [this n val]
    (new-lazyvector (.assocN @contents n val)))

  java.lang.Iterable
  (iterator [this]
    (.iterator @contents))

  clojure.lang.Associative
  (containsKey [this key]
    (.containsKey @contents key))
  (entryAt [this key]
    (.entryAt @contents key))

  clojure.lang.IPersistentCollection
  (count [this]
    (.count @contents))
  (cons [this o]
    (new-lazyvector (.cons @contents o))) ;; really deref?
  (empty [this]
    (new-lazyvector {}))
  (equiv [this o]
    (or (and (instance? LazyVector o)
             (.equiv @contents @(.-contents o)))
        (and (instance? clojure.lang.IPersistentVector o)
             (.equiv @contents o))))

  clojure.lang.Seqable
  (seq [this]
    (.seq @contents))

  clojure.lang.ILookup
  (valAt [this key]
    (.valAt @contents key))
  (valAt [this key not-found]
    (.valAt @contents key not-found))

  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key))

  java.lang.Object
  (hashCode [this]
    (.hashCode @contents))

  java.util.List)


(defn new-lazyvector
  [contents]
  (->LazyVector (if (delay? contents)
                  contents
                  (delay contents))))
