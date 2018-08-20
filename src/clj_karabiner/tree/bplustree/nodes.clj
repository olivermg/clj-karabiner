(ns clj-karabiner.tree.bplustree.nodes
  (:refer-clojure :rename {iterate iterate-clj})
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.external-memory :as em]
            [clj-karabiner.tree.cache :as c]
            [clj-karabiner.keycomparator :as kc]
            [clj-karabiner.keycomparator.partial-keycomparator :as kcp]
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



(defn internal-lookup* [{:keys [size ks vs] :as this} k {:keys [key-comparator external-memory last-visited] :as user-data}]
  ;;; TODO: it'd be more elegant to not loop here but rely on multiple dispatch
  (letfn [(next-step [step]
            (max (int (/ step 2)) 1))]
    (loop [step (next-step size)
           i (int (/ (dec size) 2))
           nlast-visited (c/store last-visited this true)]
      (let [k* (nth ks i nil)]
        (cond
          (<= (kc/cmp key-comparator k k*) 0) (let [k** (nth ks (dec i) nil)]
                                                (if (or (nil? k**) (> (kc/cmp key-comparator k k**) 0))
                                                  [k* (em/load external-memory (nth vs i nil)) nlast-visited]
                                                  (let [nstep (next-step step)
                                                        ni    (- i nstep)]
                                                    (recur nstep ni nlast-visited))))
          (> (kc/cmp key-comparator k k*) 0)  (let [k** (nth ks (inc i) nil)]
                                                (if (or (nil? k**) (<= (kc/cmp key-comparator k k**) 0))
                                                  [(or k** ::inf)  (em/load external-memory (nth vs (inc i) nil)) nlast-visited]
                                                  (let [nstep (next-step step)
                                                        ni    (+ i nstep)]
                                                    (recur nstep ni nlast-visited)))))))))

(defrecord B+TreeInternalNode [b size ks vs]

  t/TreeModifyable

  (insert* [this k v {:keys [key-comparator external-memory] :as user-data}]
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
                (let [[ks1 ks2] (split-with #(< (kc/cmp key-comparator % k) 0) ks)
                      [vs1 vs2] (split-at (count ks1) vs)
                      replace?  (= (kc/cmp key-comparator (first ks2) k) 0)
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

  (lookup* [this k user-data]
    (internal-lookup* this k user-data))

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

  (lookup-range* [this k {:keys [key-comparator leaf-neighbours last-visited] :as user-data}]
    (when (>= (kc/cmp key-comparator k (-> m keys first)) 0)
      (let [matching-keys (->> (keys m)
                               (filter #(= (kc/cmp key-comparator % k) 0)))
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

(defn- user-data [{:keys [key-comparator leaf-neighbours external-memory last-visited] :as this}]
  {:key-comparator key-comparator
   :leaf-neighbours leaf-neighbours
   :external-memory external-memory
   :last-visited last-visited})

(defrecord B+Tree [b root key-comparator external-memory leaf-neighbours last-visited]

  t/TreeModifyable

  (insert* [this k v _]
    (let [[n1 k n2 nlnbs nlv] (t/insert* root k v (user-data this))
          nroot (if (nil? n2)
                  n1
                  (let [nn (->B+TreeInternalNode b 1 [k] [n1 n2])
                        nnp (em/save external-memory nn)]
                    nnp))]
      (->B+Tree b nroot key-comparator external-memory nlnbs nlv)))

  t/TreeLookupable

  (lookup* [this k _]
    (lookup-sub root #(t/lookup* % k (user-data this))))

  (lookup-range* [this k _]
    (lookup-sub root #(t/lookup-range* % (or k []) (user-data this))))

  B+TreeLeafNodeIterable

  (iterate-leafnodes [this]
    (iterate-leafnodes root)))
