(ns clj-karabiner.keycomparator)


(defprotocol KeyComparator
  (cmp [this a b]))
