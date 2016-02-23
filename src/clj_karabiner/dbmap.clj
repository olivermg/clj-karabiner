(ns clj-karabiner.dbmap
  (:require [clj-karabiner.lazymap :as l]
            [clj-karabiner.relevancemap :as r]
            [clj-karabiner.transactionmap :as t]
            [clj-karabiner.core :as c]))


(deftype DbMap [trmap typ]

  clojure.lang.IPersistentMap
  (assoc [this key val]
    (DbMap. (.assoc trmap key val) typ))
  (assocEx [this key val]
    (DbMap. (.assoc trmap key val) typ))
  (without [this key]
    (DbMap. (.without trmap key) typ))

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
    (DbMap. (.cons trmap o) typ))
  (empty [this]
    (DbMap. {} typ))
  (equiv [this o]
    (and (or (not (satisfies? c/Typable o))
             (= typ (c/typeof o)))
         (instance? clojure.lang.IPersistentMap o)
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
    (DbMap. (r/->RelevanceMap (.relevant-keys this) (c/commit (c/all trmap))) typ))
  (revert [this]
    (DbMap. (r/->RelevanceMap (.relevant-keys this) (c/revert (c/all trmap))) typ))

  c/Referencable
  (props [this]
    (into (.empty this) (remove #(sequential? (second %)) trmap)))
  (refs [this]
    (into (.empty this) (filter #(sequential? (second %)) trmap)))

  c/Typable
  (typeof [this]
    typ))


(defn new-dbmap
  ([typ commit-fn id-props content]
   (->DbMap (r/new-relevancemap (t/new-transactionmap commit-fn content) id-props) typ))

  ([typ commit-fn content]
   (new-dbmap typ commit-fn (set (keys content)) content))

  ([typ content]
   (new-dbmap typ nil content)))
