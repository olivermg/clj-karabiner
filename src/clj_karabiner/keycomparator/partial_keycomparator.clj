(ns clj-karabiner.keycomparator.partial-keycomparator
  (:require [clj-karabiner.keycomparator :as kc]))


(defrecord PartialKeyComparator []

  kc/KeyComparator

  (cmp [this a b]
    (letfn [(coll-ctor [c]
              (cond (vector? c) vec
                    (list? c)   list*
                    (set? c)    set))

            (cmp* [a b]
              (letfn [(cmp*-atom [a b]
                        (if (or (= (type a) (type b))
                                (and (number? a) (number? b)))
                          (compare a b)
                          (compare (str a) (str b))))

                      (cmp*-coll [as bs]
                        (loop [[a & as] as
                               [b & bs] bs]
                          (let [cmpres (cmp* a b)]
                            (if (= cmpres 0)
                              (if-not (and (empty? as)
                                           (empty? bs))
                                (recur as bs)
                                0)
                              cmpres))))]

                (if-not (and (coll? a) (coll? b))
                  (cmp*-atom a b)
                  (cmp*-coll a b))))]

      (if (or (not (coll? a))
              (not (coll? b)))
        (cmp* a b)
        (let [la (count a)
              lb (count b)]
          (cond
            (= la lb) (cmp* a b)
            (< la lb) (let [ctorb (coll-ctor b)]
                        (cmp* a (->> b (take la) ctorb)))
            (> la lb) (let [ctora (coll-ctor a)]
                        (cmp* (->> a (take lb) ctora) b))))))))


(defn partial-key-comparator []
  (->PartialKeyComparator))
