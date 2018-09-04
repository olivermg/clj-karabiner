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

(defn- user-data [{:keys [key-comparator leaf-neighbours node-swapper] :as this}]
  {:key-comparator key-comparator
   :leaf-neighbours leaf-neighbours
   :node-swapper node-swapper})

(defrecord B+Tree [b root key-comparator leaf-neighbours node-kvstore]

  t/ModifyableNode

  (insert* [this k v _]
    #_(println "=== INSERT* ===" k)
    (let [t1 (cc/timestamp)
          [n1 k n2 nlnbs] (t/insert* root k v (user-data this))
          t2 (cc/timestamp)
          nroot (if (nil? n2)
                  n1
                  (->> (bpn/b+tree-internalnode b :ks [k] :vs [n1 n2] :size 1)
                       (swap/swappable-node node-kvstore)))
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
    (t/lookup* root k (user-data this)))

  (lookup-range* [this k _]
    (t/lookup-range* root k (user-data this)))

  bpn/B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    (bpn/iterate-leafnodes root)))


#_(n/extend-freeze B+Tree :b+tree [x out]
                 (->> (select-keys x #{:b :root :leaf-neighbours :key-comparator :node-kvstore})
                      #_(merge {:key-comparator (type (:key-comparator x))
                              :node-kvstore (type (:node-kvstore x))})
                      (n/freeze-to-out! out)))


(defn b+tree [& {:keys [b key-comparator node-cache node-storage root]}]
  (let [b              (or b 1000)
        node-cache     (or node-cache (kvsmc/mutable-caching-kvstore 100))
        node-storage   (or node-storage (kvsa/atom-kvstore))
        node-kvstore   (kvsch/kvstore-chain node-cache node-storage)
        key-comparator (or key-comparator (kcp/partial-key-comparator))
        root           (or root (->> (bpn/b+tree-leafnode b :key-comparator key-comparator)
                                     (swap/swappable-node node-kvstore)))]
    (map->B+Tree {:b b
                  :root root
                  :key-comparator key-comparator
                  :leaf-neighbours {}
                  :node-kvstore node-kvstore})))



;;;
;;; a few sample invocations
;;;

;;; insert a few items manually (vector keys):
#_(let [t (-> (b+tree :b 3
                    :node-cache (kvsmc/mutable-caching-kvstore 1))
            (t/insert [:a 5] 55)
            (t/insert [:a 9] 99)
            (t/insert [:a 3] 33)
            (t/insert [:a 4] 44)
            (t/insert [:a 2] 22)
            (t/insert [:a 1] 11)
            (t/insert [:b 5] 555)
            (t/insert [:b 9] 999)
            (t/insert [:b 3] 333)
            (t/insert [:b 4] 444)
            (t/insert [:b 2] 222)
            (t/insert [:b 1] 111)
            (t/insert [:c 5] 5555)
            (t/insert [:c 9] 9999)
            (t/insert [:c 3] 3333)
            (t/insert [:c 4] 4444)
            (t/insert [:c 2] 2222)
            (t/insert [:c 1] 1111))
      r1 (t/lookup t [:a 5])
      r2 (t/lookup t [:c 4])
      r3 (t/lookup t [:b 1])]
  (println "KEY-COMPARATOR CNT" (kcp/get-cnt (:key-comparator t)))
  (map :value [r1 r2 r3]))

;;; insert many generated items (numeric/atomic keys):
#_(let [trees-to-keep 1
      samples 100000
      branching-factor 1000
      kvs (take samples (repeatedly #(let [k (-> (rand-int 9000000)
                                                 (+ 1000000))]
                                       [k (str "v" k)])))
      ts (atom [])
      t (time (reduce (fn [t [k v]]
                        (let [nt (t/insert t k v)]
                          (swap! ts #(take trees-to-keep
                                           (conj % nt)))
                          #_(reset! ts t)
                          nt))
                      (b+tree :b branching-factor)
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
      t1 (-> (reduce (fn [t [k v]]
                       (let [nt (t/insert t k v)]
                         nt))
                     (b+tree :b 3)
                     kvs)
             time)
      t2 (-> (t/insert t1 [:b "y" 3] "____")
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
