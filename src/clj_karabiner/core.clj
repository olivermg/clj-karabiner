(ns clj-karabiner.core)

(defprotocol Transactionable
  (changes [this])
  (revert [this])
  (commit [this]))

(defprotocol Relevancable
  (relevant-keys [this])
  (relevants [this])
  (irrelevants [this])
  ;; as we will usually alter the behavior of =, hash etc., we should provide
  ;; functions that provide that "old" behavior:
  (=* [this other])
  (all [this]))

(defprotocol Referencable
  (props [this])
  (refs [this]))

(defprotocol Typable
  (typeof [this]))



(defprotocol Dbable
  (typeof* [this])
  (id-props* [this])
  (id-vals* [this])
  (id* [this])
  (props* [this])
  (refs* [this]))
