(ns clj-karabiner.database
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree :as bp]
            [clj-karabiner.external-memory :as em]
            [clj-karabiner.external-memory.atom :as ema]))


(defrecord FactDatabase [generation-count eavt-indices aevt-indices])
(defrecord FactDatabaseValue [eavt-index aevt-index])


(defn append [{:keys [generation-count eavt-indices aevt-indices] :as this} [e a v t :as fact]]

  (letfn [(evolve-indices [[index0 & rest-indices :as indices] k fact]
            (take generation-count
                  (-> (t/insert index0 k fact)
                      (cons indices))))]

    (->FactDatabase generation-count
                    (evolve-indices eavt-indices [e a v t] fact)
                    (evolve-indices aevt-indices [a e v t] fact))))


(defn get-database-value
  ([{:keys [eavt-indices aevt-indices] :as this} generation]
   (->FactDatabaseValue (nth eavt-indices generation)
                        (nth aevt-indices generation)))

  ([this]
   (get-database-value this 0)))


(defn lookup-eavt [{:keys [eavt-index] :as this} k]
  (t/lookup-range eavt-index k))


(defn lookup-aevt [{:keys [aevt-index] :as this} k]
  (t/lookup-range aevt-index k))


(defn database [& {:keys [b+tree-branching-factor
                          generation-count]
                   :or {b+tree-branching-factor 1000
                        generation-count 1000}}]
  (let [eavt-index (bp/b+tree b+tree-branching-factor
                              (em/external-memory (ema/atom-storage)))
        aevt-index (bp/b+tree b+tree-branching-factor
                              (em/external-memory (ema/atom-storage)))]
    (->FactDatabase generation-count (list eavt-index) (list aevt-index))))
