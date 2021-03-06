(ns clj-karabiner.tree.bplustree.nodes
  (:refer-clojure :rename {iterate iterate-clj})
  (:require [clojure.tools.logging :refer [trace debug info warn error fatal]]
            [clj-karabiner.tree :as t]
            #_[clojure.tools.logging :as log]
            [clj-karabiner.stats :as stats]))


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


#_(let [node-id-rnd (java.util.Random.)]
  (defn new-nodeid []
    ;;; NOTE:
    ;;; - .nextLong on an already initialized Random is faster than rand-int
    ;;; - we can just take a random value as node id, as the identity property is not
    ;;;   important for nodes. we don't assume that we'll generate the same node
    ;;;   more than once
    (.nextLong node-id-rnd)))


(defn- assoc-if-not-nil [m k v]
  (if-not (nil? k)
    (assoc m k v)
    m))


(defprotocol B+TreeLeafNodeIterable
  (iterate-leafnodes [this]))



(defn- cmp [a b]
  #_(trace "CMP1" a b)
  (if (and (sequential? a) (sequential? b))
    (let [minlen (min (count a) (count b))
          a (->> a (take minlen) vec)
          b (->> b (take minlen) vec)]
      #_(trace "CMP2" a b)
      (compare a b))
    (compare a b)))


(defn binary-search [coll-size cmp-fn key-fn value-fn k & {:keys [not-found]}]
  (letfn [(bs [i prev-i pprev-i]
            (let [step    (let [step* (/ (- i (or prev-i 0)) 2)]  ;; NOTE: using Math/abs is slow here
                            (max (int (max step* (- step*))) 1))
                  k*      (key-fn i)
                  cmp-val (cmp-fn i k k*)]
              (if (and (not= i prev-i) (not= i pprev-i))
                (cond
                  (< cmp-val 0) (recur (max (- i step) 0)               i prev-i)
                  (> cmp-val 0) (recur (min (+ i step) (dec coll-size)) i prev-i)
                  true          (value-fn i k k*))
                (if-not (fn? not-found)
                  not-found
                  (not-found i k k*)))))]

    (bs (int (/ coll-size 2)) nil nil)))


(defn ksvs-range-search [ks ksize vs k]
  (let [last-cmpv (volatile! 0)]
    (letfn [(key-fn [i]
              (nth ks i ::inf))

            (cmp-fn [i k k*]
              (vreset! last-cmpv (cmp k k*))
              #_(let [cmpv (compare k k*)]
                  (cond
                    (< cmpv 0) (let [k** (nth ks (dec i) nil)]
                                 (if (or (nil? k**) (> (compare k k**) 0))
                                   [0 k*]
                                   [-1]))
                    (> cmpv 0) (let [k** (nth ks (inc i) nil)]
                                 (if (or (nil? k**) (<= (compare k k**) 0))
                                   [0 (or k** ::inf)]
                                   [1]))
                    true       [0 k*])))

            (value-fn [i k k*]
              (let [ki (if (<= @last-cmpv 0) i (inc i))]
                [k* (nth vs i) ki])
              #_(let [i* (cond
                           (= k** ::inf)                         (inc i)
                           (<= (compare k** k*) 0) i
                           true                                  (inc i))]
                  [k** (nth vs i*) i i*]))

            (not-found-fn [i k k*]
              (cond
                (< @last-cmpv 0) [k* (nth vs i) i]
                (> @last-cmpv 0) (let [k* (key-fn (inc i))]
                                   [k* (nth vs (inc i)) (inc i)])
                true     (throw (ex-info "should never happen" {}))))]

      (binary-search ksize cmp-fn key-fn value-fn k :not-found not-found-fn))))


(defn ksvs-split [ks ksize vs k]
  (let [;;;[ks1 ks2] (split-with #(< (compare % k) 0) ks)
        [_ _ split-i]  (ksvs-range-search ks ksize vs k)
        [ks1 ks2]   (split-at split-i ks)
        ;;;[vs1 vs2] (split-at (count ks1) vs)
        [vs1 vs2]   (split-at split-i vs)
        ]
    [[ks1 ks2] [vs1 vs2]]))


(defn lookup-local [{:keys [size ks vs] :as this} k]
  (swap! stats/+stats+ #(update-in % [:lookups :local] inc))
  (let [[k* v* _]   (ksvs-range-search ks size vs k)]
    {:actual-k k*
     :value v*
     :values [v*]}))


(declare b+tree-internalnode b+tree-leafnode)


;;; TODO: we should be able to come up with a more elegant solution instead of ks & vs:
(defrecord B+TreeInternalNode [b size ks vs]

  t/ModifyableNode

  (node-insert [this tx k v t]
    (swap! stats/+stats+ #(update-in % [:inserts :internal] inc))
    #_(trace " => INSERT* INTERNAL" k)
    (letfn [(split [{:keys [b ks vs size] :as n}]
              (let [partition-size  (-> size (/ 2) Math/ceil int)
                    [ks1 ks2]       (partition-all partition-size ks)
                    [ks1 nk]        [(butlast ks1) (last ks1)]
                    [vs1 vs2a vs2b] (partition-all partition-size vs)
                    vs2             (concat vs2a vs2b)
                    nn1             (b+tree-internalnode b :ks ks1 :vs vs1 :size (dec partition-size))
                    nn2             (b+tree-internalnode b :ks ks2 :vs vs2 :size (- partition-size (rem size 2)))]
                [nn1 nk nn2]))

            (ins [{:keys [b ks vs size] :as n} k v]
              (if-not (= k ::inf)
                (let [[[ks1 ks2] [vs1 vs2]] (ksvs-split ks size vs k)
                      replace?  (= (cmp (first ks2) k) 0)
                      ks2       (if replace? (rest ks2) ks2)
                      vs2       (if replace? (rest vs2) vs2)
                      nsize     (if replace? size (inc size))
                      nks       (concat ks1 [k] ks2)
                      nvs       (concat vs1 [v] vs2)
                      nn        (b+tree-internalnode b :ks nks :vs nvs :size nsize)]
                  nn)
                (let [nvs (-> vs butlast (concat [v]))
                      nn  (b+tree-internalnode b :ks ks :vs nvs :size size)]
                  nn)))]

      (let [{childk :actual-k
             childv :value}         (lookup-local this k)
            [n1 nk n2 nlnbs]        (t/node-insert childv tx k v t)
            nn                      (if (nil? n2)
                                      (ins this childk n1)
                                      (-> (ins this nk n1)
                                          (ins childk n2)))]
        (if (>= (-> nn :size) b)
          (let [[n1 nk n2] (split nn)]
            [n1 nk n2 nlnbs])
          [nn nil nil nlnbs]))))

  t/LookupableNode

  (node-lookup [this k t]
    (swap! stats/+stats+ #(update-in % [:lookups :internal] inc))
    #_(trace " => LOOKUP* INTERNAL" k)
    (let [{child :value} (lookup-local this k)]
      (t/node-lookup child k t)))

  (node-lookup-range [this k t]
    #_(trace " => LOOKUP-RANGE* INTERNAL" k)
    (let [{child :value} (lookup-local this k)]
      (t/node-lookup-range child k t)))

  B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    (lazy-seq (mapcat iterate-leafnodes vs))))


(defn b+tree-internalnode [b & {:keys [ks vs size]}]
  (swap! stats/+stats+ #(update-in % [:nodes :internal] inc))
  (let [size (or size (count ks))]
    ;;; TODO: make sure ks & vs are realized, as otherwise we will run into
    ;;;   stackoverflows as soon as many data items are involved. this is because
    ;;;   lazy sequences will pile up and when realizing such a pile, stack will
    ;;;   explode
    (map->B+TreeInternalNode {:b    (short b)
                              :size (short size)
                              :ks   (doall ks)
                              :vs   (doall vs)})))


(defrecord B+TreeLeafNode [b size m]

  t/ModifyableNode

  (node-insert [this tx k v {:keys [leaf-neighbours] :as t}]
    (swap! stats/+stats+ #(update-in % [:inserts :leaf] inc))
    #_(trace " => INSERT* LEAF" k)
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
                    n1 (b+tree-leafnode b :m m1 :size n1size)
                    n2 (b+tree-leafnode b :m m2 :size n2size)
                    nleafnbs (update-leaf-neighbours n1 n2)]
                [n1 (last ks1) n2 nleafnbs]))

            (ins [k v]
              (let [nsize (if (contains? m k) size (inc size))
                    nm  (assoc m k v)
                    nn  (b+tree-leafnode b :m nm :size nsize)]
                nn))]

      (let [nn (ins k v)]
        (if (>= (-> nn :size) b)
          (let [[n1 nk n2 nlnbs] (split nn)]
            [n1 nk n2 nlnbs])
          [nn nil nil (insert-leaf-neighbours nn)]))))

  t/LookupableNode

  (node-lookup [this k t]
    (swap! stats/+stats+ #(update-in % [:lookups :leaf] inc))
    #_(trace " => LOOKUP* LEAF" k)
    (let [value (get m k)]
      {:actual-k k
       :value value
       :values [value]}))

  (node-lookup-range [this k {:keys [leaf-neighbours] :as t}]
    #_(trace " => LOOKUP-RANGE* LEAF" k (-> m keys first))
    (when (>= (cmp k (-> m keys first)) 0)
      (let [matching-keys (->> (keys m)
                               (filter #(= (cmp % k) 0)))
            {restvs :values} (when-let [next (-> (get leaf-neighbours this) :next)]
                               (t/node-lookup-range next k t))
            values (lazy-seq
                    (concat (-> (select-keys m matching-keys)
                                vals
                                vec)
                            restvs))]
        {:actual-k k
         :values values
         :value (first values)})))

  B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    [this]))


(defn b+tree-leafnode [b & {:keys [m size]}]
  (swap! stats/+stats+ #(update-in % [:nodes :leaf] inc))
  (let [m (or m (sorted-map))
        size (or size (count m))]
    (map->B+TreeLeafNode {:b    (short b)
                          :size (short size)
                          :m    m})))
