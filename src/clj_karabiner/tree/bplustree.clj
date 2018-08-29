(ns clj-karabiner.tree.bplustree
  (:refer-clojure :rename {iterate iterate-clj})
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree.nodes :as bpn]
            [clj-karabiner.tree.swappable :as swap]
            #_[clj-karabiner.kvstore :as kvs]
            [clj-karabiner.kvstore.atom :as kvsa]
            [clj-karabiner.kvstore.mutable-cache :as kvsmc]
            [clj-karabiner.kvstore.chain :as kvsch]
            [clj-karabiner.keycomparator :as kc]
            [clj-karabiner.keycomparator.partial-keycomparator :as kcp]
            #_[clojure.tools.logging :as log]))


(defn- user-data [{:keys [key-comparator leaf-neighbours node-swapper] :as this}]
  {:key-comparator key-comparator
   :leaf-neighbours leaf-neighbours
   :node-swapper node-swapper})

(defrecord B+Tree [b root key-comparator leaf-neighbours node-kvstore]

  t/ModifyableNode

  (insert* [this k v _]
    (let [[n1 k n2 nlnbs] (t/insert* root k v (user-data this))
          nroot (if (nil? n2)
                  n1
                  (->> (bpn/b+tree-internalnode b :ks [k] :vs [n1 n2] :size 1)
                       (swap/swappable-node node-kvstore)))]
      (map->B+Tree {:b b
                    :root nroot
                    :key-comparator key-comparator
                    :leaf-neighbours nlnbs
                    :node-kvstore node-kvstore})))

  t/LookupableNode

  (lookup* [this k _]
    (t/lookup* root k (user-data this)))

  (lookup-range* [this k _]
    (t/lookup-range* root k (user-data this)))

  bpn/B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    (bpn/iterate-leafnodes root)))


(defn b+tree [& {:keys [b key-comparator node-cache node-storage]}]
  (let [b (or b 1000)
        node-cache     (or node-cache (kvsmc/mutable-caching-kvstore 100))
        node-storage   (or node-storage (kvsa/atom-kvstore))
        node-kvstore   (kvsch/kvstore-chain node-cache node-storage)
        key-comparator (or key-comparator (kcp/partial-key-comparator))]
    (map->B+Tree {:b b
                  :root (->> (bpn/b+tree-leafnode b :key-comparator key-comparator)
                             (swap/swappable-node node-kvstore))
                  :key-comparator key-comparator
                  :leaf-neighbours {}
                  :node-kvstore node-kvstore})))



;;;
;;; a few sample invocations
;;;

;;; insert a few items manually (vector keys):
#_(let [t (b+tree :b 3)
      r (-> t
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
            (t/insert [:c 1] 1111)
            (t/lookup [:a 5])
            #_clojure.pprint/pprint)]
  (println "KEY-COMPARATOR CNT" (kcp/get-cnt (:key-comparator t)))
  (:value r))

;;; insert many generated items (numeric/atomic keys):
#_(let [kvs (take 10000 (repeatedly #(let [k (-> (rand-int 9000000)
                                                (+ 1000000))]
                                      [k (str "v" k)])))
      ts (atom [])
      t (time (reduce (fn [t [k v]]
                        (let [nt (t/insert t k v)]
                          (swap! ts #(take 10 (conj % nt)))
                          #_(reset! ts t)
                          nt))
                      (b+tree :b 1000)
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
  [(time (t/lookup-range t1 [:b "y"]))
   (time (t/lookup-range t2 [:b "y"]))])



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
