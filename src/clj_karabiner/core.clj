(ns clj-karabiner.core)

(defprotocol Transactionable
  (changes [this])
  (revert [this])
  (commit [this]))

(defprotocol Relevancable
  (relevant-keys [this])
  (all [this])
  (relevants [this])
  (irrelevants [this])
  (=* [this other]))

(defprotocol Referencable
  (props [this])
  (refs [this]))
