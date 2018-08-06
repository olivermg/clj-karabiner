(ns clj-karabiner.fact-database
  (:refer-clojure :rename {load load-clj})
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree :as bp]
            [clj-karabiner.external-memory :as em]
            [clj-karabiner.external-memory.atom :as ema]
            [clj-karabiner.queryengine :as qe]
            [clj-karabiner.fact-database-value :as fdv]
            [clj-karabiner.storage-backend :as sb]))


;;; NOTE: we don't need an avet index, as we can use the vaet index for [a v ...] lookups:
(defrecord FactDatabase [storage-backend generation-count eavts aevts vaets eas
                         current-t])


(defn- append-tx-to-indices [{:keys [generation-count eavts aevts vaets eas] :as this} tx-facts]

  (letfn [(evolve-indices [old-indices new-index]
            (->> old-indices
                 (cons new-index)
                 (take generation-count)))

          (append* [[eavt aevt vaet ea] [e a v t :as fact]]
            [(t/insert eavt fact      fact)
             (t/insert aevt [a e v t] fact)
             (t/insert vaet [v a e t] fact)
             (t/insert ea   [e a]     fact)  ;; to keep track of most current t that touched this [e a]
             ])]

    (let [[neavt naevt nvaet nea] (reduce append*
                                          [(first eavts) (first aevts) (first vaets) (first eas)]
                                          tx-facts)
          neavts (evolve-indices eavts neavt)
          naevts (evolve-indices aevts naevt)
          nvaets (evolve-indices vaets nvaet)
          neas   (evolve-indices eas   nea)]
      [neavts naevts nvaets neas])))


(defn- append-to-storage [{:keys [storage-backend] :as this} facts]
  (dorun (map #(sb/append storage-backend %) facts)))


(defn append [{:keys [current-t] :as this} facts & {:keys [index-only?]
                                                    :or {index-only? false}}]
  (let [t (inc current-t)
        facts (map (fn [[e a v :as fact]]
                     [e a v t])
                   facts)
        [eavts aevts vaets eas] (append-tx-to-indices this facts)]
    (when-not index-only?
      (append-to-storage this facts))
    (map->FactDatabase (merge this
                              {:current-t t
                               :eavts eavts
                               :aevts aevts
                               :vaets vaets
                               :eas eas}))))


(defn load [{:keys [storage-backend] :as this}]
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

    (transduce (tx-aggregating-xf)
               (fn [& [db facts]]
                 (if facts
                   (let [t (-> facts first (nth 3))
                         [eavts aevts vaets eas] (append-tx-to-indices db facts)]
                     (map->FactDatabase (merge db {:current-t t
                                                   :eavts eavts
                                                   :aevts aevts
                                                   :vaets vaets
                                                   :eas eas})))
                   db))
               []
               (sb/load storage-backend))))


(defn get-database-value
  ([{:keys [generation-count eavts aevts vaets eas current-t] :as this} at-t]
   (let [generation-idx (if at-t
                          (if (>= at-t 0)
                            (- current-t at-t)
                            (Math/abs at-t))
                          0)]
     (if (and (< generation-idx generation-count)
              (>= generation-idx 0))
       (-> (fdv/->FactDatabaseValue (nth eavts generation-idx)
                                    (nth aevts generation-idx)
                                    (nth vaets generation-idx)
                                    (nth eas   generation-idx))
           (qe/queryengine))
       (throw (ex-info "invalid value for at-t, it's either not remembered anymore or does not exist yet"
                       {:at-t at-t
                        :current-t current-t
                        :generation-count generation-count})))))

  ([this]
   (get-database-value this nil)))


(defn database [storage-backend & {:keys [b+tree-branching-factor
                                          generation-count]
                                   :or {b+tree-branching-factor 1000
                                        generation-count 1000}}]

  (letfn [(make-index []
            (bp/b+tree b+tree-branching-factor
                       (em/external-memory (ema/atom-storage))))]

    (map->FactDatabase {:storage-backend nil
                        :generation-count generation-count
                        :eavts (list (make-index))
                        :aevts (list (make-index))
                        :vaets (list (make-index))
                        :eas   (list (make-index))
                        :current-t 0})))
