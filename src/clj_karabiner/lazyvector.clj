(ns clj-karabiner.lazyvector)


(deftype LazyVector [contents]

  clojure.lang.IPersistentVector
  (length [this]
    (.length contents))
  (assocN [this n val]
    (LazyVector. (.assocN contents n val)))

  java.lang.Iterable
  (iterator [this]
    (.iterator contents))

  clojure.lang.Associative
  (containsKey [this key]
    (.containsKey contents key))
  (entryAt [this key]
    (.entryAt contents key))

  clojure.lang.IPersistentCollection
  (count [this]
    (.count contents))
  (cons [this o]
    (LazyVector. (.cons contents o)))
  (empty [this]
    (LazyVector. {}))
  (equiv [this o]
    (or (and (instance? LazyVector o)
             (.equiv contents (.-contents o)))
        (and (instance? clojure.lang.IPersistentVector o)
             (.equiv contents o))))

  clojure.lang.Seqable
  (seq [this]
    (.seq contents))

  clojure.lang.ILookup
  (valAt [this key]
    (let [uv (.valAt contents key)]
      (if (delay? uv)
        @uv
        uv)))
  (valAt [this key not-found]
    (let [uv (.valAt contents key not-found)]
      (if (delay? uv)
        @uv
        uv)))

  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key))

  java.lang.Object
  (hashCode [this]
    (.hashCode contents))

  java.util.List)


(defn new-lazyvector
  [contents]
  (->LazyVector contents))
