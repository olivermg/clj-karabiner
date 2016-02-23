(ns clj-karabiner.dbmap
  (:require [clj-karabiner.lazymap :as l]
            [clj-karabiner.relevancemap :as r]
            [clj-karabiner.transactionmap :as t]
            [clj-karabiner.core :as c]))


(deftype DbMap [trmap]

  clojure.lang.IPersistentMap
  (assoc [this key val]
    (DbMap. (.assoc trmap key val)))
  (assocEx [this key val]
    (DbMap. (.assoc trmap key val)))
  (without [this key]
    (DbMap. (.without trmap key)))

  java.lang.Iterable
  (iterator [this]
    (.iterator trmap))

  clojure.lang.Associative
  (containsKey [this key]
    (.containsKey trmap key))
  (entryAt [this key]
    (.entryAt trmap key))

  clojure.lang.IPersistentCollection
  (count [this]
    (.count trmap))
  (cons [this o]
    (DbMap. (.cons trmap o)))
  (empty [this]
    (DbMap. {}))
  (equiv [this o]
    (and (instance? clojure.lang.IPersistentMap o)
         (.equiv trmap o)))

  clojure.lang.Seqable
  (seq [this]
    (.seq trmap))

  clojure.lang.ILookup
  (valAt [this key]
    (.valAt trmap key))
  (valAt [this key not-found]
    (.valAt trmap key not-found))

  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key))

  java.lang.Object
  (hashCode [this]
    (.hashCode trmap))

  java.util.Map

  c/Relevancable
  (relevant-keys [this]
    (c/relevant-keys trmap))
  (all [this]
    (c/all trmap))
  (relevants [this]
    (c/relevants trmap))
  (irrelevants [this]
    (c/irrelevants trmap))
  (=* [this o]
    (c/=* trmap o))

  c/Transactionable
  (changes [this]
    (c/changes (c/all trmap)))
  (commit [this]
    (DbMap. (r/->RelevanceMap (.relevant-keys this) (c/commit (c/all trmap)))))
  (revert [this]
    (DbMap. (r/->RelevanceMap (.relevant-keys this) (c/revert (c/all trmap)))))

  c/Referencable
  (props [this]
    (into (.empty this) (remove #(sequential? (second %)) trmap)))
  (refs [this]
    (into (.empty this) (filter #(sequential? (second %)) trmap))))


(defn new-dbmap
  ([content id-props]
   (->DbMap (r/new-relevancemap (t/new-transactionmap content) id-props)))

  ([content]
   (new-dbmap content (set (keys content)))))
