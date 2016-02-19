(ns clj-karabiner.lazymap)


(defn- realize
  [val]
  (if (delay? val)
    @val
    val))


(deftype LazyMap [hashval contents]

  clojure.lang.IPersistentMap
  (assoc [this key val]
    (LazyMap. hashval (.assoc (realize contents) key val)))
  (assocEx [this key val]
    (LazyMap. hashval (.assoc (realize contents) key val)))
  (without [this key]
    (LazyMap. hashval (.without (realize contents) key)))

  java.lang.Iterable
  (iterator [this]
    (.iterator (realize contents)))

  clojure.lang.Associative
  (containsKey [this key]
    (.containsKey (realize contents) key))
  (entryAt [this key]
    (.entryAt (realize contents) key))

  clojure.lang.IPersistentCollection
  (count [this]
    (.count (realize contents)))
  (cons [this o]
    (LazyMap. hashval (.cons (realize contents) o)))
  (empty [this]
    (LazyMap. hashval {}))
  (equiv [this o]
    (or (and (instance? LazyMap o)
             (= (.hashCode this) (.hashCode o)))
        (and (instance? clojure.lang.IPersistentMap o)
             (= (.hashCode this) (.hashCode o)))))

  clojure.lang.Seqable
  (seq [this]
    (.seq (realize contents)))

  clojure.lang.ILookup
  (valAt [this key]
    (.valAt (realize contents) key))
  (valAt [this key not-found]
    (.valAt (realize contents) key not-found))

  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key))

  java.lang.Object
  (hashCode [this]
    (if (and hashval
             (delay? contents)
             (not (realized? contents)))
      hashval
      (.hashCode (realize contents))))

  java.util.Map)


(defn new-lazymap
  ([hashval contents]
   (->LazyMap hashval contents))

  ([contents]
   (new-lazymap nil contents)))
