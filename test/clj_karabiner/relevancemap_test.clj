(ns clj-karabiner.relevancemap-test
  (:require [clojure.test :refer :all]
            [clj-karabiner.relevancemap :refer :all]
            [clj-karabiner.core :as c]))

(deftest relevants-1
  (testing "should distinguish between relevant & irrelevant properties"
    (let [m1 (new-relevancemap {:a 11 :b 22 :c 33} #{:a :c})]
      (is (= (c/relevant-keys m1) #{:a :c}))
      (is (= (c/all m1) {:a 11 :b 22 :c 33}))
      (is (= (c/relevants m1) {:a 11 :c 33}))
      (is (= (c/irrelevants m1) {:b 22})))))

(deftest lookup-1
  (testing "lookup should work in all common ways"
    (let [m1 (new-relevancemap {:a 11 :b 22 :c 33})]
      (is (= (m1 :a) 11))
      (is (= (m1 :b) 22))
      (is (= (m1 :c) 33))
      (is (= (:a m1) 11))
      (is (= (:b m1) 22))
      (is (= (:c m1) 33))
      (is (= (get m1 :a) 11))
      (is (= (get m1 :b) 22))
      (is (= (get m1 :c) 33)))))

(deftest equality-1
  (testing "equality between different instances"
    (let [m1 (new-relevancemap {:a 11 :b 22 :c 33} #{:a})
          m2 (new-relevancemap {:a 11 :b 22 :c 33} #{:a})
          m3 (new-relevancemap {:a 11 :b 22 :c 33} #{:a :b})
          m4 (new-relevancemap {:a 11 :b 33 :c 44} #{:a})
          m5 (new-relevancemap {:a 22 :b 22 :c 33} #{:a})]
      (is (= m1 m2))
      (is (c/=* m1 m2))
      (is (not= m1 m3))
      (is (c/=* m1 m3))
      (is (= m1 m4))
      (is (not (c/=* m1 m4)))
      (is (not= m1 m5))
      (is (not (c/=* m1 m5))))))

(deftest equality-2
  (testing "equality with maps"
    (let [m1 (new-relevancemap {:a 11 :b 22 :c 33} #{:a :c})
          m2 {:a 11 :b 22 :c 33}
          m3 {:a 11       :c 33}
          m4 {:a 11 :b 22 :c 44}
          m5 {:a 11       :c 44}]
      (is (not= m1 m2))
      (is (c/=* m1 m2))
      (is (= m1 m3))
      (is (not (c/=* m1 m3)))
      (is (not= m1 m4))
      (is (not (c/=* m1 m4)))
      (is (not= m1 m5))
      (is (not (c/=* m1 m5))))))
