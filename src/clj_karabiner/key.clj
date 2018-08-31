(ns clj-karabiner.key)


(defprotocol ComparableKey
  (cmp [this other]))
