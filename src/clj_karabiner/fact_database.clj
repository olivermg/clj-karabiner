(ns clj-karabiner.fact-database
  (:refer-clojure :rename {load load-clj})
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree :as bp]
            [clj-karabiner.external-memory.atom :as ema]
            [clj-karabiner.fact-database.dbvalue :as dbv]
            [clj-karabiner.storage-backend :as sb]))


;;; NOTE: we don't need an avet index, as we can use the vaet index for [a v ...] lookups:
(defrecord FactDatabase [storage-backend generation-count eavts aevts vaets eas
                         current-t])


(defn- append-tx-to-indices-generations [generation-count eavts aevts vaets eas tx-facts]

  (letfn [(evolve-indices [old-indices new-index]
            (->> old-indices
                 (cons new-index)
                 (take generation-count)))

          (append-fact [[eavt aevt vaet ea] [e a v t :as fact]]
            [(t/insert eavt fact      fact)
             (t/insert aevt [a e v t] fact)
             (t/insert vaet [v a e t] fact)
             (t/insert ea   [e a]     fact)  ;; to keep track of most current t that touched this [e a]
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


(defn- append-to-storage [{:keys [storage-backend] :as this} facts]
  (dorun (map #(sb/append storage-backend %) facts)))


(defn append [{:keys [current-t generation-count eavts aevts vaets eas] :as this} facts
              & {:keys [index-only?]
                 :or {index-only? false}}]
  (let [t (inc current-t)
        facts (map (fn [[e a v :as fact]]
                     [e a v t])
                   facts)
        [eavts aevts vaets eas] (append-tx-to-indices-generations generation-count eavts aevts vaets eas facts)]
    (when-not index-only?
      (append-to-storage this facts))
    (map->FactDatabase (merge this {:current-t t
                                    :eavts eavts
                                    :aevts aevts
                                    :vaets vaets
                                    :eas eas}))))


(defn- load-from-storage [{:keys [generation-count storage-backend eavts aevts vaets eas] :as this}]
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
                                                     (into [t] (append-tx-to-indices-generations generation-count eavts aevts vaets eas tx-facts)))
                                                   args))
                                               [0 eavts aevts vaets eas]
                                               (sb/load storage-backend))]
      (map->FactDatabase (merge this {:current-t t
                                      :eavts eavts
                                      :aevts aevts
                                      :vaets vaets
                                      :eas eas})))))


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


(defn database [storage-backend & {:keys [b+tree-branching-factor
                                          generation-count]
                                   :or {generation-count 1000}}]

  (letfn [(make-index []
            (bp/b+tree :b b+tree-branching-factor))]

    (-> (map->FactDatabase {:storage-backend storage-backend
                            :generation-count generation-count
                            :eavts (list (make-index))
                            :aevts (list (make-index))
                            :vaets (list (make-index))
                            :eas   (list (make-index))
                            :current-t 0})
        (load-from-storage))))


;;;
;;; some sample invocations
;;;

#_(def db1
  (time
   (let [#_be #_(clj-karabiner.storage-backend.memory/memory-storage-backend
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
         db (-> (database be
                          :generation-count 3
                          :branching-factor 1000)
                #_(append [[:person/e1 :a1 :v1.1]
                           [:person/e1 :a2 :v2.1]])
                #_(append [[:person/e1 :a1 :v1.2]
                           [:person/e1 :a3 :v3.1]])
                #_(append [[:person/e2 :a1 :v1.1]
                           [:person/e2 :a2 :v2.1]])
                #_(append [[:person/e2 :a2 :v2.2]
                           [:person/e2 :a3 :v3.1]]))
         db-val1 (get-database-value db)]
     #_(clj-karabiner.fact-database.dbvalue/query-facts db-val1 [nil :a3 :v3.1])
     #_(clj-karabiner.fact-database.dbvalue/query db-val1 [nil :a1 :v1.1]
                                                  :project-full-entities? true)
     db)))
