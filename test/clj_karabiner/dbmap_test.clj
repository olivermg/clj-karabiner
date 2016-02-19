(ns clj-karabiner.dbmap-test
  (:require [clojure.test :refer :all]
            [clj-karabiner.dbmap :refer :all]
            [clj-karabiner.relevancemap :as r]
            [clj-karabiner.transactionmap :as t]))

(deftest laziness-1
  (testing "laziness"
    (let [m1 (new-dbmap (delay {:a 11 :b 22 :c [1 2 3]}) #{:a :c})]
      (is (= (:b m1) 22))
      (is (= (m1 :b) 22)))))

(deftest references-1
  (testing "recognition of references"
    (let [m1 (new-dbmap {:a 11 :b 22 :c [1 2 3]} #{:a :c})]
      (is (= (props m1) {:a 11 :b 22}))
      (is (= (refs m1) {:c [1 2 3]})))))

(deftest relevance-1
  (testing "relevance properties"
    (let [m1 (new-dbmap {:a 11 :b 22 :c [1 2 3]} #{:a :c})]
      (is (= (r/relevants m1) {:a 11 :c [1 2 3]}))
      (is (= (r/irrelevants m1) {:b 22})))))

(deftest transactions-1
  (testing "transaction behavior"
    (let [m1 (new-dbmap {:a 11 :b 22 :c [1 2 3]} #{:a :c})
          m2 (assoc m1 :d 44)
          m3 (dissoc m2 :b)
          m4 (merge m3 {:c [2 3 4]})]
      (is (= (t/changes m4) {:added {:d 44}
                             :changed {:c [2 3 4]}
                             :deleted {:b 22}})))))

(deftest all-1
  (testing "all features in combination"
    (let [m1 (new-dbmap {:a 11 :b 22 :c [1 2 3] :d 44} #{:a :c})
          m2 (merge m1 {:b 33 :e 55})
          m3 (dissoc m2 :a)
          m4 (t/commit m3)
          m5 (t/revert m3)]
      (is (not= m1 m2))
      (is (not= m1 m3))
      (is (= (:d m1) 44))
      (is (= (t/changes m3) {:added {:e 55}
                             :changed {:b 33}
                             :deleted {:a 11}}))
      (is (= m4 {:b 33 :c [1 2 3] :d 44 :e 55}))
      (is (= m5 {:a 11 :b 22 :c [1 2 3] :d 44})))))
