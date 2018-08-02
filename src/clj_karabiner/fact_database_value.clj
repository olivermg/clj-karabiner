(ns clj-karabiner.fact-database-value
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
;;;     (THIS IS THE CURRENT SOLUTION, implemented in clj-karabiner.queryengine)


(defrecord FactDatabaseValue [eavt aevt vaet ea])


(defn lookup-eavts [{:keys [eavt] :as this} k]
  (t/lookup-range eavt k))

(defn lookup-aevts [{:keys [aevt] :as this} k]
  (t/lookup-range aevt k))

(defn lookup-vaets [{:keys [vaet] :as this} k]
  (t/lookup-range vaet k))

(defn lookup-ea [{:keys [ea] :as this} [e a :as k]]
  {:pre [(and (not (nil? e))
              (not (nil? a)))]}
  (t/lookup ea k))
