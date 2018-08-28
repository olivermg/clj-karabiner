(ns clj-karabiner.tree.iteration
  (:require [clj-karabiner.cache :as c]))


(defrecord IterationResult [leaf-neighbours last-visited])


(defn combine [this other]
  (->IterationResult (merge (:leaf-neighbours this)
                            (:leaf-neighbours other))
                     (reduce (fn [s e]
                               (c/store s e (c/lookup (:last-visited other) e)))
                             (:last-visited this)
                             (c/keys (:last-visited other)))))
