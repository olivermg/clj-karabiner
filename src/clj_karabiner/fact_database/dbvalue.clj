(ns clj-karabiner.fact-database.dbvalue
  (:require [clj-karabiner.tree :as t]))


;;; TODO: looking up combinations that contain v returns wrong results,
;;;   because when looking up a certain v that was true in the past
;;;   but has meanwhile been overridden by a new and different v, then the
;;;   old v-state will still be found and returned.
;;;
;;;   ideas for fixing this:
;;;   - delete "old" facts from index when a new [e a v] gets inserted
;;;   - handle this during query phase (lookup without passing v, then
;;;     sort this out afterwards "manually")
;;;   - maintain another index over just [a]? (nope, as it is equivalent to
;;;     second approach above)
;;;   - make a subsequent lookup without including v in the query, look
;;;     if any other vs for that a exist with a later t value and if so,
;;;     drop fact from result
;;;     (THIS IS THE CURRENT SOLUTION, see implementation below)


(defrecord DbValue [eavt aevt vaet ea])


(defn query-facts [{:keys [eavt aevt vaet ea] :as this} [e a v :as q]]

  (letfn [(remove-out-of-date-facts [facts]  ;; need to call this in all queries that include v as part of the query
            (let [eamap (->> facts
                             (map (fn [[e a v t :as fact]]
                                    {:pre [(not (nil? e))
                                           (not (nil? a))]}
                                    (:value (t/lookup ea [e a]))))
                             (reduce (fn [s [e a v t :as fact]]
                                       (assoc s [e a] t))
                                     {}))]
              (remove (fn [[e a v t :as fact]]
                        (not= t (get eamap [e a])))
                      facts)))]

    (case (->> [(nil? e) (nil? a) (nil? v)]
               (mapv not))

      [false false false] (throw (ex-info "lookup without index key not supported" {:q q}))
      [false false true ] (remove-out-of-date-facts (:values (t/lookup-range vaet [v])))
      [false true  false] (:values (t/lookup-range aevt [a]))
      [false true  true ] (remove-out-of-date-facts (:values (t/lookup-range vaet [v a])))
      [true  false false] (:values (t/lookup-range eavt [e]))
      [true  false true ] (throw (ex-info "lookup of type [e v] not supported" {:q q}))
      [true  true  false] (:values (t/lookup-range eavt [e a]))
      [true  true  true ] (remove-out-of-date-facts (:values (t/lookup-range eavt [e a v]))))))


(defn query [this [e a v :as q] & {:keys [project-full-entities?]
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


(defn dbvalue [eavt aevt vaet ea]
  (map->DbValue {:eavt eavt
                 :aevt aevt
                 :vaet vaet
                 :ea ea}))
