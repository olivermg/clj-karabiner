(ns clj-karabiner.fact-database
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree :as bp]
            [clj-karabiner.external-memory :as em]
            [clj-karabiner.external-memory.atom :as ema]
            [clj-karabiner.queryengine :as qe]
            [clj-karabiner.fact-database-value :as fdv]))


;;; NOTE: we don't need an avet index, as we can use the vaet index for [a v ...] lookups:
(defrecord FactDatabase [generation-count eavts aevts vaets eas])


(defn append [{:keys [generation-count eavts aevts vaets eas] :as this}
              [e a v t :as fact]]

  (letfn [(evolve-indices [[index0 & rest-indices :as indices] k fact]
            (take generation-count
                  (-> (t/insert index0 k fact)
                      (cons indices))))]

    (->FactDatabase generation-count
                    (evolve-indices eavts fact      fact)
                    (evolve-indices aevts [a e v t] fact)
                    (evolve-indices vaets [v a e t] fact)
                    (evolve-indices eas   [e a]     fact)  ;; to keep track of most current t that touched this [e a]
                    )))


(defn get-database-value
  ;;; TODO: generation should be t/tx
  ([{:keys [eavts aevts vaets eas] :as this} generation]
   (-> (fdv/->FactDatabaseValue (nth eavts generation)
                                (nth aevts generation)
                                (nth vaets generation)
                                (nth eas   generation))
       (qe/queryengine)))

  ([this]
   (get-database-value this 0)))


(defn database [& {:keys [b+tree-branching-factor
                          generation-count]
                   :or {b+tree-branching-factor 1000
                        generation-count 1000}}]

  (letfn [(make-index []
            (bp/b+tree b+tree-branching-factor
                       (em/external-memory (ema/atom-storage))))]

    (->FactDatabase generation-count
                    (list (make-index))  ;; eavt
                    (list (make-index))  ;; aevt
                    (list (make-index))  ;; vaet
                    (list (make-index))  ;; ea
                    )))
