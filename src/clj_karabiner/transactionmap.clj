(ns clj-karabiner.transactionmap
  (:require [clojure.data :as d]))


(defprotocol Transactionable
  (changes [this])
  (revert [this])
  (commit [this]))


(deftype TransactionMap [original contents]

  clojure.lang.IPersistentMap
  (assoc [this key val]
    (TransactionMap. original (.assoc contents key val)))
  (assocEx [this key val]
    (TransactionMap. original (.assoc contents key val)))
  (without [this key]
    (TransactionMap. original (.without contents key)))

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
    (TransactionMap. original (.cons contents o)))
  (empty [this]
    (TransactionMap. {} {}))
  (equiv [this o]
    (and (instance? clojure.lang.IPersistentMap o)
         (.equiv contents o)))

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
    (.hashCode contents))

  java.util.Map

  Transactionable
  (changes [this]
    (let [df (d/diff original contents)]
      {:added (into {} (remove #(contains? (first df) (first %)) (second df)))
       :changed (into {} (filter #(contains? (first df) (first %)) (second df)))
       :deleted (into {} (remove #(contains? (second df) (first %)) (first df)))}))
  (revert [this]
    (TransactionMap. original original))
  (commit [this]
    (TransactionMap. contents contents)))


(defn new-transactionmap
  [contents]
  (->TransactionMap contents contents))
