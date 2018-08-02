(ns clj-karabiner.tree.bplustree
  (:refer-clojure :rename {iterate iterate-clj})
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.external-memory :as em]
            [clj-karabiner.tree.cache :as c]
            #_[clojure.tools.logging :as log]))


;;; TODO:
;;; - assign atomic key/id to each node. this will help with:
;;;   - saving nodes in external memory
;;;   - identifying nodes in last-visited (and maybe leaf-neighbours?)
;;; - introduce Split record to make splitting easier
;;; - untangle logic in nodes regarding:
;;;   - keeping track of leaf-neighbours
;;;   - keeping track of last-visited
;;;   - doing actual splitting
;;;   - saving nodes to external memory (maybe make this asynchronous
;;;     by keeping a ledger of "dirty" nodes)
;;;   - approach: separate traversal logic from "node-internal" logic:
;;;     former should handle combining results of latter
;;;   this can probably be done by returning appropriate info from
;;;   lookup & insert methods and processing above steps afterwards
;;;   (maybe even delayed)
;;; - implement better lookup logic/data model in internal nodes
;;;   regarding lookup of appropriate child node
;;; - introduce protocol/record for key comparison
;;; - implement saving nodes to external memory depending on their
;;;   last-visited state


(defn- assoc-if-not-nil [m k v]
  (if-not (nil? k)
    (assoc m k v)
    m))


#_(defprotocol B+TreeModifyable
  (insert [this k v leaf-neighbours]))

#_(defprotocol B+TreeLookupable
  (lookup [this k])
  (lookup-range [this k klen leaf-neighbours]))

(defprotocol B+TreeLeafNodeIterable
  (iterate-leafnodes [this]))


;;; TODO: wrap this into a protocol/record, so one can implement custom compare logics:
(defn- cmp-keys [k1 k2]
  (if (or (not (sequential? k1))
          (not (sequential? k2)))
    (compare k1 k2)
    (let [ctor (cond (vector? k1) vec
                     (list? k1)   list*)
          kl1 (count k1)
          kl2 (count k2)]
      (cond
        (= kl1 kl2) (compare k1 k2)
        (< kl1 kl2) (compare k1 (->> k2 (take kl1) ctor))
        (> kl1 kl2) (compare (->> k1 (take kl2) ctor) k2)))))


(defrecord B+TreeInternalNode [b size ks vs]

  t/TreeModifyable

  (insert* [this k v {:keys [external-memory] :as user-data}]
    (letfn [(split [{:keys [b ks vs size] :as n}]
              (let [partition-size  (-> size (/ 2) Math/ceil int)
                    [ks1 ks2]       (partition-all partition-size ks)
                    [ks1 nk]        [(butlast ks1) (last ks1)]
                    [vs1 vs2a vs2b] (partition-all partition-size vs)
                    vs2             (concat vs2a vs2b)
                    nn1             (->B+TreeInternalNode b (dec partition-size) ks1 vs1)
                    nn2             (->B+TreeInternalNode b (- partition-size (rem size 2)) ks2 vs2)]
                [nn1 nk nn2]))

            (ins [{:keys [b ks vs size] :as n} k v]
              (if-not (= k ::inf)
                (let [[ks1 ks2] (split-with #(< (cmp-keys % k) 0) ks)
                      [vs1 vs2] (split-at (count ks1) vs)
                      replace?  (= (cmp-keys (first ks2) k) 0)
                      ks2       (if replace? (rest ks2) ks2)
                      vs2       (if replace? (rest vs2) vs2)
                      nsize     (if replace? size (inc size))
                      nks       (concat ks1 [k] ks2)
                      nvs       (concat vs1 [v] vs2)
                      nn        (->B+TreeInternalNode b nsize nks nvs)]
                  nn)
                (let [nvs (-> vs butlast (concat [v]))
                      nn  (->B+TreeInternalNode b size ks nvs)]
                  nn)))]

      (let [[childk childv lv] (t/lookup* this k user-data)
            [n1 nk n2 nlnbs]   (t/insert* childv k v user-data)
            nn                 (if (nil? n2)
                                 (ins this childk n1)
                                 (-> (ins this nk n1)
                                     (ins childk n2)))]
        (if (>= (-> nn :size) b)
          (let [[n1 nk n2] (split nn)
                n1p (em/save external-memory n1)
                n2p (em/save external-memory n2)]
            [n1p nk n2p nlnbs lv])
          (let [nnp (em/save external-memory nn)]
            [nnp nil nil nlnbs lv])))))

  t/TreeLookupable

  (lookup* [this k {:keys [external-memory last-visited] :as user-data}]
    ;;; TODO: it'd be more elegant to not loop here but rely on multiple dispatch
    (loop [[k* & ks*] ks
           [v* & vs*] vs
           nlast-visited (c/store last-visited this true)]
      (cond
        (nil? k*)              [::inf (em/load external-memory v*) nlast-visited]
        (<= (cmp-keys k k*) 0) [k*    (em/load external-memory v*) nlast-visited]
        true                   (recur ks* vs* nlast-visited))))

  (lookup-range* [this k user-data]
    (t/lookup* this k user-data))

  B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    (lazy-seq (mapcat iterate-leafnodes vs))))


(defrecord B+TreeLeafNode [b size m]

  t/TreeModifyable

  (insert* [this k v {:keys [leaf-neighbours external-memory last-visited] :as user-data}]
    (letfn [(insert-leaf-neighbours [n1]
              (let [{pleaf :prev nleaf :next} (get leaf-neighbours this)
                    {ppleaf :prev}            (when-not (nil? pleaf)
                                                (get leaf-neighbours pleaf))
                    {nnleaf :next}            (when-not (nil? nleaf)
                                                (get leaf-neighbours nleaf))]
                (-> leaf-neighbours
                    (assoc-if-not-nil pleaf {:prev ppleaf :next n1})
                    (dissoc this)
                    (assoc-if-not-nil n1    {:prev pleaf  :next nleaf})
                    (assoc-if-not-nil nleaf {:prev n1     :next nnleaf}))))

            (update-leaf-neighbours [n1 n2]
              (let [{pleaf :prev nleaf :next} (get leaf-neighbours this)
                    {ppleaf :prev}            (when-not (nil? pleaf)
                                                (get leaf-neighbours pleaf))
                    {nnleaf :next}            (when-not (nil? nleaf)
                                                (get leaf-neighbours nleaf))]
                (-> leaf-neighbours
                    (assoc-if-not-nil pleaf {:prev ppleaf :next n1})
                    (dissoc this)
                    (assoc-if-not-nil n1    {:prev pleaf  :next n2})
                    (assoc-if-not-nil n2    {:prev n1     :next nleaf})
                    (assoc-if-not-nil nleaf {:prev n2     :next nnleaf}))))

            (split [{:keys [b m size] :as n}]
              (let [partition-size (-> size (/ 2) Math/ceil int)
                    [ks1 ks2] (partition-all partition-size (keys m))
                    m1 (apply dissoc m ks2)
                    m2 (apply dissoc m ks1)
                    n1size partition-size
                    n2size (- partition-size (rem size 2))
                    n1  (->B+TreeLeafNode b n1size m1)
                    n2  (->B+TreeLeafNode b n2size m2)
                    nleafnbs (update-leaf-neighbours n1 n2)]
                [n1 (last ks1) n2 nleafnbs]))

            (ins [k v]
              (let [nsize (if (contains? m k) size (inc size))
                    nm  (assoc m k v)
                    nn  (->B+TreeLeafNode b nsize nm)]
                nn))]

      (let [nn (ins k v)]
        (if (>= (-> nn :size) b)
          (let [[n1 nk n2 nlnbs] (split nn)
                n1p (em/save external-memory n1)
                n2p (em/save external-memory n2)]
            [n1p nk n2p nlnbs last-visited])
          (let [nnp (em/save external-memory nn)]
            [nnp nil nil (insert-leaf-neighbours nn) last-visited])))))

  t/TreeLookupable

  (lookup* [this k {:keys [last-visited] :as user-data}]
    [k (get m k) (c/store last-visited this true)])

  (lookup-range* [this k {:keys [leaf-neighbours last-visited] :as user-data}]
    (when (>= (cmp-keys k (-> m keys first)) 0)
      (let [matching-keys (->> (keys m)
                               (filter #(= (cmp-keys % k) 0)))
            nlast-visited (c/store last-visited this true)
            [_ restvs restvisited] (when-let [next (-> (get leaf-neighbours this) :next)]
                                     (t/lookup-range* next k (assoc user-data
                                                                    :last-visited nlast-visited)))]
        ;;; TODO: we don't really need k in this case, as it is only needed for when we get
        ;;;   invoked by insert. as this never happens for lookup-range, we could get rid of
        ;;;   k (i.e. result in 2-tuple vector form). however, currently lookup* (below) relies
        ;;;   on the format to be a 2-tuple, so we leave it like that for the moment:
        [k
         (lazy-seq
          (concat (-> (select-keys m matching-keys)
                      vals
                      vec)
                  restvs))
         (or restvisited nlast-visited)])))

  B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    [this]))


(defn- lookup-sub [node lookup-fn]
  (loop [[_ v] (lookup-fn node)]
    (if (satisfies? t/TreeLookupable v)
      (recur (lookup-fn v))
      v)))

(defn- user-data [{:keys [leaf-neighbours external-memory last-visited] :as this}]
  {:leaf-neighbours leaf-neighbours
   :external-memory external-memory
   :last-visited last-visited})

(defrecord B+Tree [b root external-memory leaf-neighbours last-visited]

  t/TreeModifyable

  (insert* [this k v _]
    (let [[n1 k n2 nlnbs nlv] (t/insert* root k v (user-data this))
          nroot (if (nil? n2)
                  n1
                  (let [nn (->B+TreeInternalNode b 1 [k] [n1 n2])
                        nnp (em/save external-memory nn)]
                    nnp))]
      (->B+Tree b nroot external-memory nlnbs nlv)))

  t/TreeLookupable

  (lookup* [this k _]
    (lookup-sub root #(t/lookup* % k (user-data this))))

  (lookup-range* [this k _]
    (lookup-sub root #(t/lookup-range* % (or k []) (user-data this))))

  B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    (iterate-leafnodes root)))


(defn b+tree [b external-memory]
  (->B+Tree b (->B+TreeLeafNode b 0 (sorted-map)) external-memory {} (c/sized-cache 3)))



;;;
;;; a few sample invocations
;;;

;;; insert a few items manually (vector keys):
#_(let [as (clj-karabiner.external-memory.atom/atom-storage #_clj-karabiner.tree.bplustree-proxy/edn-readers)
      em (clj-karabiner.external-memory/external-memory as)]
  (-> (b+tree 3 em)
      (t/insert [:a 5] 55)
      (t/insert [:a 9] 99)
      (t/insert [:a 3] 33)
      (t/insert [:a 4] 44)
      (t/insert [:a 2] 22)
      (t/insert [:a 1] 11)
      (t/lookup [:a 5])
      #_clojure.pprint/pprint)
  #_(-> em :memory-storage :a deref first))

;;; insert many generated items (numeric/atomic keys):
#_(let [as (clj-karabiner.external-memory.atom/atom-storage)
      em (clj-karabiner.external-memory/external-memory as)
      kvs (take 100000 (repeatedly #(let [k (-> (rand-int 9000000)
                                                (+ 1000000))]
                                      [k (str "v" k)])))
      ts (atom [])
      t (time (reduce (fn [t [k v]]
                        (let [nt (t/insert t k v)]
                          (swap! ts #(take 10 (conj % nt)))
                          #_(reset! ts t)
                          nt))
                      (b+tree 100 em)
                      kvs))
      kv1 (first kvs)
      k1 (first kv1)]
  #_(Thread/sleep 120000)
  [kv1 (count @ts) (time (t/lookup t k1))])

;;; testing range lookup (vector keys):
#_(let [storage (clj-karabiner.external-memory/external-memory (clj-karabiner.external-memory.atom/atom-storage))
        kvs (for [k1 [:c :a :b]
                k2 ["x" "z" "y"]
                k3 (range 50)]
            [[k1 k2 k3] (str (name k1) k2 (format "%02d" k3))])
      t1 (-> (reduce (fn [t [k v]]
                       (let [nt (t/insert t k v)]
                         nt))
                     (b+tree 3 storage)
                     kvs)
             time)
      t2 (-> (t/insert t1 [:b "y" 3] "____")
             time)]
  #_(clojure.pprint/pprint t2)
  [storage
   (time (t/lookup-range t1 [:b "y"]))
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
