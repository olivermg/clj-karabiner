(ns clj-karabiner.tree.bplustree.nodes-test
  (:require [clojure.test :refer :all]
            [clj-karabiner.tree.bplustree.nodes :refer :all]
            [clj-karabiner.tree.bplustree :as bpt]
            [clj-karabiner.keycomparator.partial-keycomparator :as pkc]
            [clj-karabiner.external-memory.atom :as aem]
            [clj-karabiner.tree.cache :as c]))


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
               :size (count ks)}))

          (build-userdata []
            (let [key-comparator (pkc/partial-key-comparator)
                  external-memory (aem/atom-external-memory)
                  last-visited (c/sized-cache 3)]
              {:key-comparator key-comparator
               :external-memory external-memory
               :leaf-neighbours {}
               :last-visited last-visited}))]

    (let [this (build-this)
          user-data (build-userdata)]
      (testing "min lookup"
        (let [[k v] (internal-lookup* this 1 user-data)]
          (is (= 2 k))
          (is (= [1 2] v))))
      (testing "lookup 1"
        (let [[k v] (internal-lookup* this 7 user-data)]
          (is (= 9 k))
          (is (= [7 8 9] v))))
      (testing "lookup 2"
        (let [[k v] (internal-lookup* this 4 user-data)]
          (is (= 6 k))
          (is (= [3 4 5 6] v))))
      (testing "lookup 3"
        (let [[k v] (internal-lookup* this 23 user-data)]
          (is (= 24 k))
          (is (= [23 24] v))))
      (testing "lookup 4"
        (let [[k v] (internal-lookup* this 29 user-data)]
          (is (= 30 k))
          (is (= [28 29 30] v))))
      (testing "max lookup"
        (let [[k v] (internal-lookup* this 321 user-data)]
          (is (= :clj-karabiner.tree.bplustree.nodes/inf k))
          (is (= [31 32 33] v)))))

    (let [this (build-this-2)
          user-data (build-userdata)]
      (testing "max lookup"
        (let [[k v] (internal-lookup* this [:a 2] user-data)]
          (is (= [:a 4] k))
          (is (= {:b 3 :size 2 :m {[:a 3] 33 [:a 4] 44}} v)))))

    (let [this (build-this-3)
          user-data (build-userdata)]
      (testing "lookup 1"
        (let [[k v] (internal-lookup* this 1 user-data)]
          (is (= 3 k))
          (is (= [1 2 3] v))))
      (testing "lookup 2"
        (let [[k v] (internal-lookup* this 5 user-data)]
          (is (= :clj-karabiner.tree.bplustree.nodes/inf k))
          (is (= [4 5 6] v)))))))
