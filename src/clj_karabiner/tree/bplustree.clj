(ns clj-karabiner.tree.bplustree
  (:refer-clojure :rename {iterate iterate-clj})
  (:require [clj-karabiner.tree :as t]
            #_[sample.rangemap :as rm]
            #_[sample.partiallycomparablekey :as pck]
            #_[sample.memoryfactstore :as mfs]
            #_[clojure.tools.logging :as log]))


(defn assoc-if-not-nil [m k v]
  (if-not (nil? k)
    (assoc m k v)
    m))


(defprotocol B+TreeModifyable
  (insert [this k v leaf-neighbours]))

(defprotocol B+TreeLookupable
  (lookup [this k])
  (lookup-range [this k klen leaf-neighbours]))

(defprotocol B+TreeLeafNodeIterable
  (iterate-leafnodes [this]))


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

  B+TreeModifyable

  (insert [this k v leaf-neighbours]
    (letfn [(split [{:keys [b ks vs size] :as n}]
              (let [partition-size  (-> size (/ 2) Math/ceil int)
                    [ks1 ks2]       (partition-all partition-size ks)
                    [ks1 nk]        [(butlast ks1) (last ks1)]
                    [vs1 vs2a vs2b] (partition-all partition-size vs)
                    vs2             (concat vs2a vs2b)]
                [(->B+TreeInternalNode b (dec partition-size) ks1 vs1)
                 nk
                 (->B+TreeInternalNode b (- partition-size (rem size 2)) ks2 vs2)]))

            (ins [{:keys [b ks vs size] :as n} k v]
              (if-not (= k ::inf)
                (let [[ks1 ks2] (split-with #(< (cmp-keys % k) 0) ks)
                      [vs1 vs2] (split-at (count ks1) vs)
                      replace?  (= (cmp-keys (first ks2) k) 0)
                      ks2       (if replace? (rest ks2) ks2)
                      vs2       (if replace? (rest vs2) vs2)
                      nsize     (if replace? size (inc size))
                      nks       (concat ks1 [k] ks2)
                      nvs       (concat vs1 [v] vs2)]
                  (->B+TreeInternalNode b nsize nks nvs))
                (let [nvs (-> vs butlast (concat [v]))]
                  (->B+TreeInternalNode b size ks nvs))))]

      (let [[childk childv]  (lookup this k)
            [n1 nk n2 nlnbs] (insert childv k v leaf-neighbours)
            nn               (if (nil? n2)
                               (ins this childk n1)
                               (-> (ins this nk n1)
                                   (ins childk n2)))]
        (if (>= (-> nn :size) b)
          (concat (split nn) [nlnbs])
          [nn nil nil nlnbs]))))

  B+TreeLookupable

  (lookup [this k]
    (loop [[k* & ks*] ks
           [v* & vs*] vs]
      (cond
        (nil? k*)              [::inf v*]
        (<= (cmp-keys k k*) 0) [k* v*]
        true                   (recur ks* vs*))))

  (lookup-range [this k _ _]
    (lookup this k))

  B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    (lazy-seq (mapcat iterate-leafnodes vs))))


(defrecord B+TreeLeafNode [b size m]

  B+TreeModifyable

  (insert [this k v leaf-neighbours]
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
                    n1 (->B+TreeLeafNode b n1size m1)
                    n2 (->B+TreeLeafNode b n2size m2)
                    nleafnbs (update-leaf-neighbours n1 n2)]
                [n1 (last ks1) n2 nleafnbs]))

            (ins [k v]
              (let [nsize (if (contains? m k) size (inc size))
                    nm (assoc m k v)]
                (->B+TreeLeafNode b nsize nm)))]

      (let [nn (ins k v)]
        (if (>= (-> nn :size) b)
          (split nn)
          [nn nil nil (insert-leaf-neighbours nn)]))))

  B+TreeLookupable

  (lookup [this k]
    [k (get m k)])

  (lookup-range [this k klen leaf-neighbours]
    (when (>= (cmp-keys k (-> m keys first)) 0)
      (let [matching-keys (->> (keys m)
                               (filter #(= (cmp-keys % k) 0)))]
        ;;; TODO: we don't really need k in this case, as it is only needed for when we get
        ;;;   invoked by insert. as this never happens for lookup-range, we could get rid of
        ;;;   k (i.e. result in 2-tuple vector form). however, currently lookup* (below) relies
        ;;;   on the format to be a 2-tuple, so we leave it like that for the moment:
        [k (lazy-seq
            (concat (-> (select-keys m matching-keys)
                        vals
                        vec)
                    (when-let [next (-> (get leaf-neighbours this) :next)]
                      (second (lookup-range next k klen leaf-neighbours)))))])))

  B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    [this]))


(defn lookup* [node lookup-fn]
  (loop [[_ v] (lookup-fn node)]
    (if (satisfies? B+TreeLookupable v)
      (recur (lookup-fn v))
      v)))

(defrecord B+Tree [b root leaf-neighbours]

  t/TreeModifyable

  (insert [this k v]
    (let [[n1 k n2 nlnbs] (insert root k v leaf-neighbours)
          nroot (if (nil? n2)
                  n1
                  (->B+TreeInternalNode b 1 [k] [n1 n2]))]
      (->B+Tree b nroot nlnbs)))

  t/TreeLookupable

  (lookup [this k]
    (lookup* root #(lookup % k)))

  (lookup-range* [this k]
    (lookup* root #(lookup-range % (or k []) (count k) leaf-neighbours)))

  B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    (iterate-leafnodes root)))


(defn b+tree [b]
  (->B+Tree b (->B+TreeLeafNode b 0 (sorted-map)) {}))



;;;
;;; a few sample invocations
;;;

#_(-> (b+tree 3)
    (t/insert [:a 5] 55)
    (t/insert [:a 9] 99)
    (t/insert [:a 3] 33)
    (t/insert [:a 4] 44)
    (t/insert [:a 2] 22)
    (t/insert [:a 1] 11)
    (t/lookup-range [:a 3]))

#_(let [kvs (take 100000 (repeatedly #(let [k (-> (rand-int 9000000)
                                                (+ 1000000))]
                                      [k (str "v" k)])))
      ts (atom [])
      t (time (reduce (fn [t [k v]]
                        (let [nt (t/insert t k v)]
                          (swap! ts #(take 10 (conj % nt)))
                          #_(reset! ts t)
                          nt))
                      (b+tree 100)
                      kvs))
      kv1 (first kvs)
      k1 (first kv1)]
  #_(Thread/sleep 120000)
  [kv1 (count @ts) (time (t/lookup t k1))])

#_(let [kvs (for [k1 [:c :a :b]
                k2 ["x" "z" "y"]
                k3 (range 50)]
            [[k1 k2 k3] (str (name k1) k2 (format "%02d" k3))])
      t1 (-> (reduce (fn [t [k v]]
                       (let [nt (t/insert t k v)]
                         nt))
                     (b+tree 3)
                     kvs)
             time)
      t2 (-> (t/insert t1 [:b "y" 3] "____")
             time)]
  #_(clojure.pprint/pprint t2)
  [(time (t/lookup-range t1 [:b "y"]))
   (time (t/lookup-range t2 [:b "y"]))])