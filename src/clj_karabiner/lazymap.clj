(ns clj-karabiner.lazymap)


(deftype LazyMap [contents]

  clojure.lang.IPersistentMap
  (assoc [this key val]
    (LazyMap. (.assoc contents key val)))
  (assocEx [this key val]
    (LazyMap. (.assoc contents key val)))
  (without [this key]
    (LazyMap. (.without contents key)))

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
    (LazyMap. (.cons contents o)))
  (empty [this]
    (LazyMap. {}))
  (equiv [this o]
    (or (and (instance? LazyMap o)
             (.equiv contents (.-contents o)))
        (and (instance? clojure.lang.IPersistentMap o)
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

  java.util.Map)


(defn new-lazymap
  [contents]
  (->LazyMap contents))
