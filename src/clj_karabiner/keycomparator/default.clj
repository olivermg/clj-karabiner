(ns clj-karabiner.keycomparator.default
  (:require [clj-karabiner.keycomparator :as kc]))


(defrecord DefaultKeyComparator []

  kc/KeyComparator

  (cmp [this a b]
    (compare a b)))


(defn default-key-comparator []
  (->DefaultKeyComparator))
