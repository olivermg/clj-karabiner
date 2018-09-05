(ns clj-karabiner.tree.bplustree.nodes-test
  (:require [clojure.test :refer :all]
            [clj-karabiner.tree.bplustree.nodes :refer :all]
            [clj-karabiner.tree.bplustree :as bpt]
            [clj-karabiner.keycomparator.partial-keycomparator :as pkc]
            #_[clj-karabiner.kvstore.atom :as kva]
            #_[clj-karabiner.cache :as c]
            [clojure.pprint]))


(deftest internal-node-lookup-logic-1
  (letfn [(build-this []
            (let [ks [     2         6       9             13       15          18       20       22       24          27          30]
                  vs [[1 2] [3 4 5 6] [7 8 9] [10 11 12 13]  [14 15]  [16 17 18]  [19 20]  [21 22]  [23 24]  [25 26 27]  [28 29 30]  [31 32 33]]]
              {:ks ks
               :vs vs
               :size (count ks)}))

          (build-this-2 []
            (let [ks [[:a 4] [:a 5]]
                  vs [{:b 3, :size 2, :m {[:a 3] 33, [:a 4] 44}}
                      {:b 3, :size 1, :m {[:a 5] 55}}
                      {:b 3, :size 1, :m {[:a 9] 99}}]]
              {:ks ks
               :vs vs
               :size (count ks)}))

          (build-this-3 []
            (let [ks [       3       ]
                  vs [[1 2 3] [4 5 6]]]
              {:ks ks
               :vs vs
               :size (count ks)}))]

    (let [this (build-this)
          kc (pkc/partial-key-comparator)]
      (testing "min lookup"
        (let [{k :actual-k v :value} (lookup-local this 1 kc)]
          (is (= 2 k))
          (is (= [1 2] v))))
      (testing "lookup 1"
        (let [{k :actual-k v :value} (lookup-local this 7 kc)]
          (is (= 9 k))
          (is (= [7 8 9] v))))
      (testing "lookup 2"
        (let [{k :actual-k v :value} (lookup-local this 4 kc)]
          (is (= 6 k))
          (is (= [3 4 5 6] v))))
      (testing "lookup 3"
        (let [{k :actual-k v :value} (lookup-local this 23 kc)]
          (is (= 24 k))
          (is (= [23 24] v))))
      (testing "lookup 4"
        (let [{k :actual-k v :value} (lookup-local this 29 kc)]
          (is (= 30 k))
          (is (= [28 29 30] v))))
      (testing "max lookup"
        (let [{k :actual-k v :value} (lookup-local this 321 kc)]
          (is (= :clj-karabiner.tree.bplustree.nodes/inf k))
          (is (= [31 32 33] v)))))

    (let [this (build-this-2)
          kc (pkc/partial-key-comparator)]
      (testing "max lookup"
        (let [{k :actual-k v :value} (lookup-local this [:a 2] kc)]
          (is (= [:a 4] k))
          (is (= {:b 3 :size 2 :m {[:a 3] 33 [:a 4] 44}} v)))))

    (let [this (build-this-3)
          kc (pkc/partial-key-comparator)]
      (testing "lookup 1"
        (let [{k :actual-k v :value} (lookup-local this 1 kc)]
          (is (= 3 k))
          (is (= [1 2 3] v))))
      (testing "lookup 2"
        (let [{k :actual-k v :value} (lookup-local this 5 kc)]
          (is (= :clj-karabiner.tree.bplustree.nodes/inf k))
          (is (= [4 5 6] v)))))))


(deftest ksvs-range-search-test
  (testing "various searches"
    ;;; TODO: make this a real test
    (let [ks [     2         6       9             13       15          18       20       22       24          27          30]
          vs [[1 2] [3 4 5 6] [7 8 9] [10 11 12 13]  [14 15]  [16 17 18]  [19 20]  [21 22]  [23 24]  [25 26 27]  [28 29 30]  [31 32 33]]
          kc (pkc/partial-key-comparator)
          results (mapv #(into [%] (ksvs-range-search ks (count ks) vs % kc))
                        [-2 0 1 2 3 4 5 6 7 8 9 12 15 18 22 28 30 33 1000])]
      (clojure.pprint/pprint results)
      (is (every? vector? results)))))
