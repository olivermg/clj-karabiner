(ns clj-karabiner.dbmap-test
  (:require [clojure.test :refer :all]
            [clj-karabiner.dbmap :refer :all]
            [clj-karabiner.relevancemap :as r]
            [clj-karabiner.transactionmap :as t]
            [clj-karabiner.lazymap :as lm]
            [clj-karabiner.lazyvector :as lv]
            [clj-karabiner.core :as c]))


(deftest references-1
  (testing "recognition of references"
    (let [m1 (new-dbmap nil #{:a :c} {:a 11 :b 22 :c [1 2 3]})]
      (is (= (c/props m1) {:a 11 :b 22}))
      (is (= (c/refs m1) {:c [1 2 3]})))))

(deftest relevance-1
  (testing "relevance properties"
    (let [m1 (new-dbmap nil #{:a :c} {:a 11 :b 22 :c [1 2 3]})]
      (is (= (c/relevant-keys m1) #{:a :c}))
      (is (= (c/all m1) {:a 11 :b 22 :c [1 2 3]}))
      (is (= (c/relevants m1) {:a 11 :c [1 2 3]}))
      (is (= (c/irrelevants m1) {:b 22})))))

(deftest transactions-1
  (testing "transaction behavior"
    (let [m1 (new-dbmap nil #{:a :c} {:a 11 :b 22 :c [1 2 3]})
          m2 (assoc m1 :d 44)
          m3 (dissoc m2 :b)
          m4 (merge m3 {:c [2 3 4]})]
      (is (= (c/changes m4) {:added {:d 44}
                             :changed {:c [2 3 4]}
                             :deleted {:b 22}})))))

(deftest all-1
  (testing "all features in combination"
    (let [m1 (new-dbmap nil #{:a :c} {:a 11 :b 22 :c [1 2 3] :d 44})
          m2 (merge m1 {:b 33 :e 55})
          m3 (dissoc m2 :a)
          m4 (c/commit m3)
          m5 (c/revert m3)]
      (is (= m1 m2))
      (is (not= m1 m3))
      (is (= (:d m1) 44))
      (is (= (c/changes m3) {:added {:e 55}
                             :changed {:b 33}
                             :deleted {:a 11}}))
      (is (= (c/all m4) {:b 33 :c [1 2 3] :d 44 :e 55}))
      (is (= (c/all m5) {:a 11 :b 22 :c [1 2 3] :d 44}))
      (is (c/=* m4 {:b 33 :c [1 2 3] :d 44 :e 55}))
      (is (c/=* m5 {:a 11 :b 22 :c [1 2 3] :d 44}))
      (is (= (c/relevants m4) {:c [1 2 3]}))
      (is (= (c/relevants m5) {:a 11 :c [1 2 3]}))
      (is (= m4 {:c [1 2 3]}))
      (is (= m5 {:a 11 :c [1 2 3]}))
      (is (= (c/irrelevants m4) {:b 33 :d 44 :e 55}))
      (is (= (c/irrelevants m5) {:b 22 :d 44})))))

(deftest use-case-1
  (testing "real world use case: create & persist new instance"
    (let [r11 (lm/new-lazymap (delay (println "triggered lazy load of r11")
                                     {:r11 :r11}) ;; this would lazily load stuff from the db
                              )
          r12 (lm/new-lazymap (delay (throw (ex-info "db error" {}))) ;; this mimics an error on lazy load
                              )
          r13 (lm/new-lazymap (delay (println "triggered lazy load of r13")
                                     {:r13 :r13}) ;; this would lazily load stuff from the db
                              )

          m (new-dbmap {:p1 11 :p2 "22" :p3 :33
                        :r1 [r11 r12 r13]})]
      (is (= (c/props m) {:p1 11 :p2 "22" :p3 :33}))
      (is (= (:p1 m) 11))
      (is (= (m :p2) "22"))
      (is (= (get m :p3) :33))
      (is (= (-> (c/refs m) :r1 first) {:r11 :r11})) ;; lookup of r11 doesn't trigger exception
      (is (= (-> (c/refs m) :r1 (nth 2)) {:r13 :r13})) ;; neither lookup of r13
      (is (thrown? Exception (:r12 (-> (c/refs m) :r1 second)))) ;; but looking up property of r12 does
      ;;...
      )))

(deftest use-case-2
  (testing "real world use case: create & persist & change new instance"
    (let [r11 {:r11 :r11}
          m1 (new-dbmap nil
                        #{:p1 :r1}
                        {:p1 11 :p2 "22" :p3 :33
                         :r1 [r11]})
          m2 (assoc m1 :p2 "222")
          m3 (assoc m2 :p1 111)
          m4 (merge m3 {:p4 444})
          m5 (dissoc m4 :p3)
          m6 (c/commit m5)]
      (is (= (c/changes m1) {:added {}
                             :changed {}
                             :deleted {}}))
      (is (= (c/changes m2) {:added {}
                             :changed {:p2 "222"}
                             :deleted {}}))
      (is (= (c/changes m3) {:added {}
                             :changed {:p1 111 :p2 "222"}
                             :deleted {}}))
      (is (= (c/changes m4) {:added {:p4 444}
                             :changed {:p1 111 :p2 "222"}
                             :deleted {}}))
      (is (= (c/changes m5) {:added {:p4 444}
                             :changed {:p1 111 :p2 "222"}
                             :deleted {:p3 :33}}))
      (is (= (c/changes m6) {:added {}
                             :changed {}
                             :deleted {}}))
      (is (= m1 m2))
      (is (= m3 m4 m5))
      (is (not (c/=* m1 m2)))
      (is (not= m1 m3))
      (is (not (c/=* m1 m3)))
      (is (= m6 {:p1 111 :r1 [r11]}))
      (is (c/=* m6 {:p1 111 :p2 "222" :p4 444 :r1 [r11]}))
      )))

(deftest use-case-2-error
  (testing "real world use case error on lazy access"
    (let [r1 (lv/new-lazyvector (delay [1 2 3])) ;; simulating lazy loading
          r2 (lv/new-lazyvector (delay (throw (ex-info "db error" {})))) ;; simulating error on lazy loading
          m1 (new-dbmap {:p1 11 :p2 "22" :p3 :33
                         :r1 r1 :r2 r2})]
      (is (= (c/props m1) {:p1 11 :p2 "22" :p3 :33}))
      (is (= ((c/refs m1) :r1) [1 2 3]))
      (is (thrown? Exception (hash ((c/refs m1) :r2)))) ;; accessing anything within r2 triggers lazy loading
      )))
