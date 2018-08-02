(ns clj-karabiner.fact-database
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree :as bp]
            [clj-karabiner.external-memory :as em]
            [clj-karabiner.external-memory.atom :as ema]
            [clj-karabiner.queryengine :as qe]
            [clj-karabiner.fact-database-value :as fdv]))


;;; NOTE: we don't need an avet index, as we can use the vaet index for [a v ...] lookups:
(defrecord FactDatabase [generation-count eavts aevts vaets eas
                         current-t])


(defn append [{:keys [generation-count eavts aevts vaets eas current-t] :as this} facts]

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

    (let [new-t (inc current-t)
          [neavt naevt nvaet nea] (->> facts
                                       (map (fn [[e a v :as fact]]
                                              [e a v new-t]))
                                       (reduce append*
                                               [(first eavts) (first aevts) (first vaets) (first eas)]))
          neavts (evolve-indices eavts neavt)
          naevts (evolve-indices aevts naevt)
          nvaets (evolve-indices vaets nvaet)
          neas   (evolve-indices eas   nea)]
      (->FactDatabase generation-count neavts naevts nvaets neas new-t))))


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
                    0)))
