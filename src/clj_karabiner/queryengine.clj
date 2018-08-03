(ns clj-karabiner.queryengine
  (:require [clj-karabiner.fact-database-value
             :refer [lookup-eavts lookup-aevts lookup-vaets lookup-ea]
             :as fdv]))


;;; engine for doing querying in the sense of CQRS


(defrecord QueryEngine [db-val])


(defn query-facts [{:keys [db-val] :as this} [e a v :as q]]

  (letfn [(remove-out-of-date-facts [facts]  ;; need to call this in all queries that include v as part of the query
            (let [eamap (->> facts
                             (map (fn [[e a v t :as fact]]
                                    (lookup-ea db-val [e a])))
                             (reduce (fn [s [e a v t :as fact]]
                                       (assoc s [e a] t))
                                     {}))]
              (remove (fn [[e a v t :as fact]]
                        (not= t (get eamap [e a])))
                      facts)))]

    (case (->> [(nil? e) (nil? a) (nil? v)]
               (mapv not))

      [false false false] (throw (ex-info "lookup without index key not supported" {:q q}))
      [false false true ] (remove-out-of-date-facts (lookup-vaets db-val [v]))
      [false true  false] (lookup-aevts db-val [a])
      [false true  true ] (remove-out-of-date-facts (lookup-vaets db-val [v a]))
      [true  false false] (lookup-eavts db-val [e])
      [true  false true ] (throw (ex-info "lookup of type [e v] not supported" {:q q}))
      [true  true  false] (lookup-eavts db-val [e a])
      [true  true  true ] (remove-out-of-date-facts (lookup-eavts db-val [e a v])))))


(defn query [{:keys [db-val] :as this} [e a v :as q] & {:keys [project-full-entities?]
                                                        :or {project-full-entities? false}}]

  (letfn [(build-entities [sorted-facts]
            (->> sorted-facts
                 (reduce (fn [entities [e a v _ :as fact]]
                           (assoc-in entities [e a] v))
                         {})
                 (map (fn [[id entity]]
                        (assoc entity :db/id id)))))

          (query-full-entity-facts [facts]
            (->> facts
                 (map first)
                 set
                 (mapcat #(query-facts this [% nil nil]))))]

    (let [facts (query-facts this q)
          facts (if-not project-full-entities?
                  facts
                  (query-full-entity-facts facts))]
      (->> facts
           (sort (fn [[_ _ _ t1] [_ _ _ t2]]
                   (compare t1 t2)))
           (build-entities)))))


(defn queryengine [database-value]
  (->QueryEngine database-value))



;;;
;;; some sample invocations
;;;

#_(let [db (-> (clj-karabiner.fact-database/database)
             (clj-karabiner.fact-database/append [[:e1 :a1 :v1.1]
                                                  [:e1 :a2 :v2.1]])
             (clj-karabiner.fact-database/append [[:e1 :a1 :v1.2]
                                                  [:e1 :a3 :v3.1]])
             (clj-karabiner.fact-database/append [[:e2 :a1 :v1.1]
                                                  [:e2 :a2 :v2.1]])
             (clj-karabiner.fact-database/append [[:e2 :a2 :v2.2]
                                                  [:e2 :a3 :v3.1]]))
      db-val1 (clj-karabiner.fact-database/get-database-value db)]
  (query db-val1 [nil :a3 :v3.1]))
