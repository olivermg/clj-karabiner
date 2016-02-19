(ns clj-karabiner.lazymap-test
  (:require [clojure.test :refer :all]
            [clj-karabiner.lazymap :refer :all]
            [clojure.data :as d]))

(deftest basic-1
  (testing "basic properties of this custom datatype"
    (let [m1 (new-lazymap {:a [1 2 3] :b 22 :c 33})
          m11 (new-lazymap {:a [1 2 3] :b 22 :c 33})
          m2 (assoc m1 :c 333)
          m3 (assoc m2 :c 3333)
          m4 (dissoc m3 :b)
          m5 (merge m4 {:d 44})]
      (is (not (sequential? m1)))
      (is (not (seq? m1)))
      (is (coll? m1))
      (is (map? m1))
      (is (instance? clojure.lang.IPersistentMap m1))
      (is (instance? clojure.lang.IPersistentCollection m1))
      (is (instance? java.util.Map m1))
      (is (= m1 m1))
      (is (= m1 m11))
      (is (= m1 {:a [1 2 3] :b 22 :c 33}))
      (is (= m2 m2))
      (is (= (type m1) (type m2) (type m3) (type m4) (type m5)
             clj_karabiner.lazymap.LazyMap))
      (is (= (d/diff (.-contents m1) (.-contents m5)) '({:b 22 :c 33} {:c 3333 :d 44} {:a [1 2 3]})))
      (is (= (d/diff m1 m5) '({:b 22 :c 33} {:c 3333 :d 44} {:a [1 2 3]}))))))

(deftest laziness-1
  (testing "lazyness"
    (let [m1 (new-lazymap {:a 11 :b (delay 22)})]
      (is (= (m1 :a) 11))
      (is (= (m1 :b) 22))
      (is (= (:b m1) 22)))))
