(ns clj-karabiner.transactionmap-test
  (:require [clojure.test :refer :all]
            [clj-karabiner.transactionmap :refer :all]))

(deftest basics-1
  (testing "test basic properties of this custom datatype"
    (let [m1 (new-transactionmap {:a 11 :b 22 :c 33})]
      (is (not (seq? m1)))
      (is (not (sequential? m1)))
      (is (coll? m1))
      (is (map? m1))
      (is (instance? java.util.Map m1)))))

(deftest changes-1
  (testing "changes should be kept track of"
    (let [m1 (new-transactionmap {:a 11 :b 22 :c 33})
          m2 (assoc m1 :d 44)
          m3 (merge m2 {:c 333})
          m4 (dissoc m3 :b)]
      (is (= (changes m4) {:added {:d 44}
                           :changed {:c 333}
                           :deleted {:b 22}})))))

(deftest commit-1
  (testing "committing should result in original := updated state"
    (let [m1 (new-transactionmap {:a 11 :b 22 :c 33})
          m2 (merge m1 {:c 333 :d 44})
          m3 (commit m2)]
      (is (= m3 {:a 11 :b 22 :c 333 :d 44}))
      (is (= (changes m3) {:added {}
                           :changed {}
                           :deleted {}})))))

(deftest revert-1
  (testing "reverting should result in original state"
    (let [m1 (new-transactionmap {:a 11 :b 22 :c 33})
          m2 (merge m1 {:c 333 :d 44})
          m3 (revert m2)]
      (is (= m3 {:a 11 :b 22 :c 33}))
      (is (= (changes m3) {:added {}
                           :changed {}
                           :deleted {}})))))
