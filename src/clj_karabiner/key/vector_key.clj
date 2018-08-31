(ns clj-karabiner.key.vector-key
  (:require [clj-karabiner.key :as k]))


(defrecord VectorKey [keyval keynum]

  k/ComparableKey

  (cmp [this other]
    (let [kn (compare ^Long keynum
                      ^Long (:keynum other))]
      (if-not (= kn 0)
        kn
        (compare keyval (:keyval other))))))


(defn vector-key [keyval]
  )



#_(letfn [(str->val [s]
          (let [ba (.getBytes s "UTF-8")]
            (reduce (fn [s e]
                      (+ (* s 16r10) e))
                    0 (take 3 ba))))
        (kn [v]
          (reduce (fn [s f]
                    (let [f (cond
                              (keyword? f) (str->val (name f))
                              (number? f)  f
                              (string? f)  (str->val f))]
                      (+ (* s 16r100000) f)))
                  0 v))]
  (let [v1 [:b 1 "abc"]
        v2 [:a 100000 "abc"]
        k1 (kn v1)
        k2 (kn v2)]
    [k1 k2 (< k1 k2)]))
