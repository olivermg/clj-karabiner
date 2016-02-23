(ns clj-karabiner.dbentity
  (:require [clj-karabiner.core :as c]
            [digest :refer [md5 sha-512]]
            [clojure.string :as s]))


(def default-behavior
  {:typeof* (fn [this]
              (:type this))

   :id-props* (fn [this]
                (:id-props this))

   :id-vals* (fn [this]
               (map #(let [v (second %)]
                       (if (satisfies? c/Dbable v)
                         (c/id* v)
                         v))
                    (filter #(contains? (c/id-props* this) (first %))
                            (:data this))))

   :id* (fn [this]
          (let [idv (:id this)]
            (if (nil? idv)
              (keyword (md5 (s/join "<:>" (c/id-vals* this))))
              idv)))

   :props* (fn [this]
             (into {} (remove #(coll? (second %)) (:data this))))

   :refs* (fn [this]
            (into {} (filter #(coll? (second %)) (:data this))))})


(defrecord DbEntity [type id-props id data])


(extend DbEntity
  c/Dbable
  default-behavior)
