(ns clj-karabiner.tree.iteration
  (:require [clj-karabiner.kvstore :as kvs]))


(defrecord IterationResult [leaf-neighbours last-visited])


#_(defn combine [this other]
  (->IterationResult (merge (:leaf-neighbours this)
                            (:leaf-neighbours other))
                     (reduce (fn [s e]
                               (kvs/store s e (c/lookup (:last-visited other) e)))
                             (:last-visited this)
                             (kvs/keys (:last-visited other)))))
