(ns clj-karabiner.relevancemap)


(defprotocol Relevancable
  (relevants [this])
  (irrelevants [this])
  (=* [this other]))


(deftype RelevanceMap [hashkeys contents]

  clojure.lang.IPersistentMap
  (assoc [this key val]
    (RelevanceMap. hashkeys (.assoc contents key val)))
  (assocEx [this key val]
    (RelevanceMap. hashkeys (.assoc contents key val)))
  (without [this key]
    (RelevanceMap. hashkeys (.without contents key)))

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
    (RelevanceMap. hashkeys (.cons contents o)))
  (empty [this]
    (RelevanceMap. #{} {}))
  (equiv [this o]
    (or (and (satisfies? Relevancable o)
             (.equiv (relevants this) (relevants o)))
        (and (instance? clojure.lang.IPersistentMap o)
             (.equiv (relevants this) o))))

  clojure.lang.Seqable
  (seq [this]
    (.seq contents))

  clojure.lang.ILookup
  (valAt [this key]
    (.valAt contents key))
  (valAt [this key not-found]
    (.valAt contents key not-found))

  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key))

  java.lang.Object
  (hashCode [this]
    (.hashCode (relevants this)))

  java.util.Map

  Relevancable
  (relevants [this]
    (into {} (filter #(contains? hashkeys (first %))
                     contents)))
  (irrelevants [this]
    (into {} (remove #(contains? hashkeys (first %))
                     contents)))
  (=* [this o]
    (or (and (satisfies? Relevancable o)
             (= contents (.-contents o)))
        (= contents o))))

(defn new-relevancemap
  ([data relevant-keys]
   {:pre [(map? data)]}
   (->RelevanceMap relevant-keys data))

  ([data]
   (new-relevancemap data (set (keys data)))))
