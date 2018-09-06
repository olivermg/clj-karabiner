(ns clj-karabiner.fact-database
  (:refer-clojure :rename {load load-clj})
  (:require [taoensso.nippy :as n]
            [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree :as bp]
            [clj-karabiner.tree.swappable :as s]
            [clj-karabiner.fact-database.dbvalue :as dbv]
            [clj-karabiner.storage-backend :as sb]
            [clj-karabiner.kvstore :as kvs]))


;;; NOTE: we don't need an avet index, as we can use the vaet index for [a v ...] lookups:
(defrecord FactDatabase [storage-backend generation-count key-comparator node-cache node-storage
                         eavts aevts vaets eas current-t current-storage-position])


(defn- append-tx-to-indices-generations [t generation-count eavts aevts vaets eas tx-facts]

  (letfn [(evolve-indices [old-indices new-index]
            (->> old-indices
                 (cons new-index)
                 (take generation-count)))

          (append-fact [[eavt aevt vaet ea] [e a v t :as fact]]
            [(t/insert eavt t fact      fact)
             (t/insert aevt t [a e v t] fact)
             (t/insert vaet t [v a e t] fact)
             (t/insert ea   t [e a]     fact)  ;; to keep track of most current t that touched this [e a]
             ])

          (append-tx-to-indices [eavt aevt vaet ea tx-facts]
            (reduce append-fact
                    [eavt aevt vaet ea]
                    tx-facts))]

    (let [[neavt naevt nvaet nea] (append-tx-to-indices (first eavts) (first aevts) (first vaets) (first eas) tx-facts)
          neavts (evolve-indices eavts neavt)
          naevts (evolve-indices aevts naevt)
          nvaets (evolve-indices vaets nvaet)
          neas   (evolve-indices eas   nea)]
      [neavts naevts nvaets neas])))


(defn- append-to-storage [{:keys [storage-backend current-storage-position] :as this} facts]
  (reduce (fn [pos fact]
            (sb/append storage-backend fact))
          current-storage-position
          facts))


(defn append [{:keys [current-t current-storage-position generation-count eavts aevts vaets eas] :as this} facts
              & {:keys [index-only?]
                 :or {index-only? false}}]
  (let [t (inc current-t)
        facts (map (fn [[e a v :as fact]]
                     [e a v t])
                   facts)
        ncsp (if-not index-only?
               (append-to-storage this facts)
               current-storage-position)
        [eavts aevts vaets eas] (append-tx-to-indices-generations t generation-count eavts aevts vaets eas facts)]

    (map->FactDatabase (merge this {:current-t t
                                    :current-storage-position ncsp
                                    :eavts eavts
                                    :aevts aevts
                                    :vaets vaets
                                    :eas eas}))))


(defn rebuild-indices [{:keys [generation-count storage-backend eavts aevts vaets eas] :as this}]
  (println "REBUILD-INDICES")
  (letfn [(tx-aggregating-xf []
            (fn [xf]
              (let [prev-t (volatile! ::none)
                    facts (volatile! (transient []))]
                (fn
                  ([]
                   (xf))

                  ([txs]
                   (xf txs (persistent! @facts)))

                  ([txs [e a v t :as fact]]
                   (when (= @prev-t ::none)
                     (vreset! prev-t t))
                   (if (= t @prev-t)
                     (do
                       (conj! @facts fact)
                       txs)
                     (let [txfacts (persistent! @facts)]
                       (vreset! prev-t t)
                       (vreset! facts (transient [fact]))
                       (xf txs txfacts))))))))]

    (let [[t eavts aevts vaets eas] (transduce (tx-aggregating-xf)
                                               (fn [& [[t eavts aevts vaets eas :as args] tx-facts]]
                                                 (if tx-facts
                                                   (let [t (or (-> tx-facts first (nth 3))  ;; tx-facts can be empty when storage is empty
                                                               0)]
                                                     (into [t] (append-tx-to-indices-generations t generation-count eavts aevts vaets eas tx-facts)))
                                                   args))
                                               [0 eavts aevts vaets eas]
                                               (sb/load storage-backend))]
      (map->FactDatabase (merge this {:current-t t
                                      :eavts eavts
                                      :aevts aevts
                                      :vaets vaets
                                      :eas eas})))))


#_(defn- thaw-indices [{:keys [index-freeze-kvstore] :as this}]
  (println "THAW-INDICES")
  (when index-freeze-kvstore
    (letfn [(thaw-index [k]
              (when-let [v (kvs/lookup index-freeze-kvstore k)]
                (n/thaw v)))]
      {:current-t (when-let [v (kvs/lookup index-freeze-kvstore :current-t)]
                    (n/thaw v))
       :eavts (thaw-index :eavts)
       :aevts (thaw-index :aevts)
       :vaets (thaw-index :vaets)
       :eas   (thaw-index :eas)})))


#_(defn- restore-indices [this]
  (let [{:keys [current-t eavts aevts vaets eas] :as indices} (thaw-indices this)]
    (if (not-any? nil? (vals indices))
      (map->FactDatabase (merge this indices))
      (map->FactDatabase (merge this (rebuild-indices this))))))


#_(defn- freeze-indices [{:keys [index-freeze-kvstore current-t eavts aevts vaets eas] :as this}]
  (letfn [(freeze-index [k v]
            (->> (n/freeze v)
                 (kvs/store index-freeze-kvstore k)))]
    (freeze-index :eavts eavts)
    (freeze-index :aevts aevts)
    (freeze-index :vaets vaets)
    (freeze-index :eas eas)
    (kvs/store index-freeze-kvstore :current-t (n/freeze current-t))))


(defn get-database-value
  ([{:keys [generation-count eavts aevts vaets eas current-t] :as this} at-t]
   (let [generation-idx (if at-t
                          (if (>= at-t 0)
                            (- current-t at-t)
                            (Math/abs at-t))
                          0)]
     (if (and (< generation-idx generation-count)
              (>= generation-idx 0))
       (dbv/dbvalue (nth eavts generation-idx)
                    (nth aevts generation-idx)
                    (nth vaets generation-idx)
                    (nth eas   generation-idx))
       (throw (ex-info "invalid value for at-t, it's either not remembered anymore or does not exist yet"
                       {:at-t at-t
                        :current-t current-t
                        :generation-count generation-count})))))

  ([this]
   (get-database-value this nil)))


(defn current-version [{:keys [current-t] :as this}]
  current-t)


(defn database [storage-backend & {:keys [branching-factor
                                          key-comparator
                                          node-kvstore
                                          generation-count]
                                   :or {generation-count 1000}}]

  (letfn [(make-index []
            (bp/b+tree :b branching-factor
                       :key-comparator key-comparator
                       :node-kvstore node-kvstore))]

    (-> (map->FactDatabase {:storage-backend storage-backend
                            :generation-count generation-count
                            :key-comparator key-comparator
                            :node-kvstore node-kvstore
                            :eavts (list (make-index))
                            :aevts (list (make-index))
                            :vaets (list (make-index))
                            :eas   (list (make-index))
                            :current-t 0})
        #_(restore-indices))))


#_(defn load-indices [{:keys [node-kvstore] :as this}]
  (when-let [root-nodeids (kvs/lookup node-kvstore ::root-nodeids)]
    {:eavts (map #(s/swappable-node node-kvstore {:id %}))
     :aevts (map #(s/swappable-node node-kvstore {:id %}))
     :vaets (map #(s/swappable-node node-kvstore {:id %}))
     :eas   (map #(s/swappable-node node-kvstore {:id %}))}))


#_(defn save-indices [{:keys [node-kvstore eavts aevts vaets eas] :as this}]
  (let [data {:eavts (map t/id eavts)
              :aevts (map t/id aevts)
              :vaets (map t/id vaets)
              :eas   (map t/id eas)}]
    (kvs/store node-kvstore ::root-nodeids data)))


#_(defn save-db [this filename]
  (n/freeze-to-file filename this))


#_(defn load-db [filename & augment-map]
  (merge (n/thaw-from-file filename)
         augment-map))


;;;
;;; some sample invocations
;;;

#_(def db1
  (let [kcmp (clj-karabiner.keycomparator.partial-keycomparator/partial-key-comparator)
        #_be #_(clj-karabiner.storage-backend.memory/memory-storage-backend
                [[:person/e0 :a1 :v1.1 1]
                 [:person/e0 :a2 :v2.1 1]
                 [:person/e0 :a3 :v3.1 1]
                 [:person/e0 :a3 :v3.2 2]])
        be (clj-karabiner.storage-backend.kafka/kafka-storage-backend
            :topic-prefix "factdb"
            :topic-fn (fn [[e a v t :as fact]]
                        (namespace e))
            :key-fn (fn [[e a v t :as fact]]
                      (name e))
            :value-fn (fn [fact]
                        fact))
        nkvs (clj-karabiner.kvstore.redis/redis-kvstore
              "redis://localhost"
              :clj->store (fn [v]
                            (if (instance? clj_karabiner.tree.bplustree.nodes.B+TreeLeafNode v)
                              (assoc v :m (into (array-map)
                                                (:m v)))
                              v))
              :store->clj (fn [v]
                            (if (instance? clj_karabiner.tree.bplustree.nodes.B+TreeLeafNode v)
                              (clj-karabiner.tree.bplustree.nodes/map->B+TreeLeafNode
                               (assoc v :m (into (sorted-map-by
                                                  #(clj-karabiner.keycomparator/cmp kcmp %1 %2))
                                                 (:m v))))
                              v)))
        #_(clj-karabiner.kvstore.chain/kvstore-chain
         (clj-karabiner.kvstore.mutable-cache/mutable-caching-kvstore 100)
         (clj-karabiner.kvstore.atom/atom-kvstore))
        db (time
            (-> #_(load-db "./testdb")
                (database be
                          :generation-count 3
                          :branching-factor 1000
                          :node-kvstore nkvs
                          :key-comparator kcmp)
                (rebuild-indices)
                #_(append [[:person/e1 :a1 :v1.1]
                           [:person/e1 :a2 :v2.1]])
                #_(append [[:person/e1 :a1 :v1.2]
                           [:person/e1 :a3 :v3.1]])
                #_(append [[:person/e2 :a1 :v1.1]
                           [:person/e2 :a2 :v2.1]])
                #_(append [[:person/e2 :a2 :v2.2]
                           [:person/e2 :a3 :v3.1]])))
        db-val1 (get-database-value db)]
    (time
     #_(clj-karabiner.fact-database.dbvalue/query-facts db-val1 [nil :a3 :v3.1])
     #_(clj-karabiner.fact-database.dbvalue/query db-val1 [nil :a1 :v1.1]
                                                  :project-full-entities? true)
     #_(clj-karabiner.fact-database.dbvalue/query db-val1 [nil :length 2]
                                                  :project-full-entities? true)
     db)))
