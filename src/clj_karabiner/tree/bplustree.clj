(ns clj-karabiner.tree.bplustree
  (:refer-clojure :rename {iterate iterate-clj})
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree.nodes :as bpn]
            [clj-karabiner.tree.swappable :as swap]
            [clj-karabiner.kvstore :as kvs]
            [clj-karabiner.kvstore.atom :as kvsa]
            [clj-karabiner.kvstore.mutable-cache :as kvsmc]
            [clj-karabiner.kvstore.chain :as kvsch]
            [clj-karabiner.keycomparator :as kc]
            [clj-karabiner.keycomparator.partial-keycomparator :as kcp]
            #_[clojure.tools.logging :as log]
            [criterium.core :as cc]
            #_[taoensso.nippy :as n]))


(def stats (volatile! {:count 0
                       :min Integer/MAX_VALUE
                       :max 0
                       :mean 0
                       :sum 0
                       :gcsum 0
                       :gccount 0
                       :prevheap 0}))


(defrecord B+Tree [b root key-comparator leaf-neighbours node-kvstore]

  t/ModifyableNode

  (insert* [this tx k v _]
    #_(println "=== INSERT* ===" k)
    (let [t1 (cc/timestamp)
          [n1 k n2 nlnbs] (t/insert root tx k v :tree this)
          t2 (cc/timestamp)
          nroot (if (nil? n2)
                  (-> (swap/get-node n1 node-kvstore)  ;; NOTE: to flag this node as "root" in kvstore - we'll overwrite potential older root within the same transaction, but that should be ok
                      (swap/swappable-node node-kvstore tx :key-id "root"))
                  (-> (bpn/b+tree-internalnode b :ks [k] :vs [n1 n2] :size 1)
                      (swap/swappable-node node-kvstore tx :key-id "root")))
          r (map->B+Tree {:b b
                          :root nroot
                          :key-comparator key-comparator
                          :leaf-neighbours nlnbs
                          :node-kvstore node-kvstore})]
      (vswap! stats
              (fn [{:keys [count min max mean sum gcsum prevheap gccount]}]
                (let [td (long (/ (- t2 t1) 1000))
                      count (inc count)
                      heap (cc/heap-used)
                      gc? (< heap prevheap)
                      gccount (if-not gc?
                                gccount
                                (do (println "GC  " (long (/ t1 1000)))
                                    (inc gccount)))]
                  (when (> td 100000)
                    (println "SLOW" (long (/ t1 1000)) td))
                  {:count count
                   :min (clojure.core/min min td)
                   :max (clojure.core/max max td)
                   :mean (int (+ mean (/ (- td mean) count)))
                   :sum (+ sum td)
                   :gcsum (if-not gc? gcsum (+ gcsum td))
                   :gccount gccount
                   :prevheap heap})))
      r))

  t/LookupableNode

  (lookup* [this k _]
    #_(println "=== LOOKUP* ===" k)
    (t/lookup root k :tree this))

  (lookup-range* [this k _]
    (t/lookup-range root k :tree this))

  bpn/B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    (bpn/iterate-leafnodes root)))


#_(n/extend-freeze B+Tree :b+tree [x out]
                 (->> (select-keys x #{:b :root :leaf-neighbours :key-comparator :node-kvstore})
                      #_(merge {:key-comparator (type (:key-comparator x))
                              :node-kvstore (type (:node-kvstore x))})
                      (n/freeze-to-out! out)))


(defn b+tree [& {:keys [b key-comparator node-kvstore root]}]
  (let [b              (or b 1000)
        node-kvstore   (or node-kvstore (kvsch/kvstore-chain (kvsmc/mutable-caching-kvstore 100)
                                                             (kvsa/atom-kvstore)))
        key-comparator (or key-comparator (kcp/partial-key-comparator))
        root           (or root (-> (bpn/b+tree-leafnode b :key-comparator key-comparator)
                                    (swap/swappable-node node-kvstore 0 :key-id "root")))]
    (map->B+Tree {:b b
                  :root root
                  :key-comparator key-comparator
                  :leaf-neighbours {}
                  :node-kvstore node-kvstore})))



;;;
;;; a few sample invocations
;;;

;;; insert a few items manually (vector keys):
#_(let [node-kvstore (clj-karabiner.kvstore.redis/redis-kvstore "redis://localhost")
      t (-> (b+tree :b 3
                    :node-kvstore node-kvstore)
            (t/insert-tx 1 {[:a 5] 55
                            [:a 9] 99
                            [:a 3] 33
                            [:a 4] 44
                            [:a 2] 22
                            [:a 1] 11})
            (t/insert-tx 2 {[:b 5] 555
                            [:b 9] 999
                            [:b 3] 333
                            [:b 4] 444
                            [:b 2] 222
                            [:b 1] 111})
            (t/insert-tx 3 {[:c 5] 5555
                            [:c 9] 9999
                            [:c 3] 3333
                            [:c 4] 4444
                            [:c 2] 2222
                            [:c 1] 1111}))
      r1 (t/lookup t [:a 5])
      r2 (t/lookup t [:c 4])
      r3 (t/lookup t [:b 1])]
  (println "KEY-COMPARATOR CNT" (kcp/get-cnt (:key-comparator t)))
  (map :value [r1 r2 r3]))

;;; insert many generated items (numeric/atomic keys):
#_(let [trees-to-keep 1
      samples 1000
      branching-factor 100
      node-kvstore (clj-karabiner.kvstore.redis/redis-kvstore "redis://localhost")
      kvs (take samples (repeatedly #(let [k (-> (rand-int 9000000)
                                                 (+ 1000000))]
                                       [k (str "v" k)])))
      ts (atom [])
      i (volatile! 0)
      t (time (reduce (fn [t [k v]]
                        (vswap! i inc)
                        (let [tx (int (/ @i 10))
                              nt (t/insert t tx k v)]
                          (swap! ts #(take trees-to-keep
                                           (conj % nt)))
                          nt))
                      (b+tree :b branching-factor
                              :node-kvstore node-kvstore)
                      kvs))
      kv1 (first kvs)
      k1 (first kv1)]
  #_(Thread/sleep 120000)
  (println "KEY-COMPARATOR CNT" (kcp/get-cnt (:key-comparator t)))
  [kv1 (count @ts) (time (t/lookup t k1))])

;;; testing range lookup (vector keys):
#_(let [kvs (for [k1 [:c :a :b]
                k2 ["x" "z" "y"]
                k3 (range 50)]
            [[k1 k2 k3] (str (name k1) k2 (format "%02d" k3))])
      i (volatile! 0)
      t1 (-> (reduce (fn [t [k v]]
                       (vswap! i inc)
                       (let [tx (int (/ @i 10))
                             nt (t/insert t tx k v)]
                         nt))
                     (b+tree :b 3)
                     kvs)
             time)
      t2 (-> (t/insert t1 (inc (int (/ @i 10))) [:b "y" 3] "____")
             time)]
  #_(clojure.pprint/pprint t2)
  [(time (:values (t/lookup-range t1 [:b "y"])))
   (time (:values (t/lookup-range t2 [:b "y"])))])



;;;
;;; dummy serialization via transit
;;;

#_(let [out (java.io.ByteArrayOutputStream.)
            writer (tr/writer out :json {:handlers {Foo (reify com.cognitect.transit.WriteHandler
                                                          (tag [_ o] "Foo")
                                                          (rep [_ o] {:a (:a o)})
                                                          (stringRep [_ _ ] nil)
                                                          (getVerboseHandler [_] nil))}})]
        (tr/write writer {:a 11})
        (tr/write writer [11 22 33])
        (tr/write writer (->Foo 22 33))
        (println (.toString out))
        (let [in (java.io.ByteArrayInputStream. (.toByteArray out))
              reader (tr/reader in :json {:handlers {"Foo" (reify com.cognitect.transit.ReadHandler
                                                            (fromRep [_ rep]
                                                              (->Foo (:a rep) :xxx)))}})]
          [(tr/read reader) (tr/read reader) (tr/read reader)]))
