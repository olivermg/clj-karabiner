(ns clj-karabiner.lazyvector-test
  (:require [clojure.test :refer :all]
            [clj-karabiner.lazyvector :refer :all]
            [clojure.data :as d]))

(deftest basic-1
  (testing "basic properties of this custom datatype"
    (let [v1 (new-lazyvector [1 2 3])
          v11 (new-lazyvector [1 2 3])
          v2 (conj v1 4)
          v3 (cons 0 v1)]
      (is (sequential? v1))
      (is (not (seq? v1)))
      (is (coll? v1))
      (is (vector? v1))
      (is (instance? clojure.lang.IPersistentVector v1))
      (is (instance? clojure.lang.IPersistentCollection v1))
      (is (instance? java.util.List v1))
      (is (= v1 v1))
      (is (= v1 v11))
      (is (= v1 [1 2 3]))
      (is (not= v1 v2))
      (is (= v2 [1 2 3 4]))
      (is (= v3 [0 1 2 3]))
      (is (= (type v1) (type v2) clj_karabiner.lazyvector.LazyVector))
      (is (= (type v3) clojure.lang.Cons)))))

(deftest laziness-1
  (testing "laziness"
    (let [v1 (new-lazyvector [1 (delay 2) 3])]
      (is (= (v1 0) 1))
      (is (= (v1 1) 2))
      (is (= (v1 2) 3)))))
