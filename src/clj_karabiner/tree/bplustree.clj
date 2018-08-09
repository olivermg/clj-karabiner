(ns clj-karabiner.tree.bplustree
  (:refer-clojure :rename {iterate iterate-clj})
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree.nodes :as bpn]
            [clj-karabiner.external-memory :as em]
            [clj-karabiner.external-memory.atom :as ema]
            [clj-karabiner.tree.cache :as c]
            [clj-karabiner.keycomparator :as kc]
            [clj-karabiner.keycomparator.partial-keycomparator :as kcp]
            #_[clojure.tools.logging :as log]))


(defn b+tree [& {:keys [b key-comparator external-memory]}]
  (let [b (or b 1000)
        external-memory (or external-memory (ema/atom-external-memory))
        key-comparator (or key-comparator (kcp/partial-key-comparator))]
    (bpn/map->B+Tree {:b b
                      :root (bpn/->B+TreeLeafNode b 0 (sorted-map-by #(kc/cmp key-comparator %1 %2)))
                      :key-comparator key-comparator
                      :external-memory external-memory
                      :leaf-neighbours {}
                      :last-visited (c/sized-cache 3)})))



;;;
;;; a few sample invocations
;;;

;;; insert a few items manually (vector keys):
#_(-> (b+tree :b 3)
    (t/insert [:a 5] 55)
    (t/insert [:a 9] 99)
    (t/insert [:a 3] 33)
    (t/insert [:a 4] 44)
    (t/insert [:a 2] 22)
    (t/insert [:a 1] 11)
    (t/lookup [:a 5])
    #_clojure.pprint/pprint)

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
                      (b+tree :b 100)
                      kvs))
      kv1 (first kvs)
      k1 (first kv1)]
  #_(Thread/sleep 120000)
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
