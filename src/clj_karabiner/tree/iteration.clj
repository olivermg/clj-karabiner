(ns clj-karabiner.tree.iteration
  (:require [clj-karabiner.tree.cache :as c]))


(defrecord IterationResult [leaf-neighbours last-visited])


(defn combine [this other]
  (->IterationResult (merge (:leaf-neighbours this)
                            (:leaf-neighbours other))
                     (reduce (fn [s e]
                               (c/store s %))
                             (:last-visited this))))
