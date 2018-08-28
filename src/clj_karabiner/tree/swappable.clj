(ns clj-karabiner.tree.swappable
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.cache :as c]
            [clj-karabiner.external-memory :as em]))


(defrecord NodeSwapper [cache storage])


(defn swap-in [{:keys [cache storage] :as this} id]
  (if-let [node (c/lookup cache id)]
    node
    (if-let [node (em/load storage id)]
      (do (c/store cache id node)
          node)
      (throw (ex-info "unknown node with id" {:id id})))))


(defn swap-out [{:keys [cache storage] :as this} id node]
  #_(c/store cache id node)
  (em/save storage node)
  nil)


(declare swappable-node)


(defrecord SwappableNode [swapper id]

  t/Node

  (id [this]
    (:id this))

  t/ModifyableNode

  (insert* [this k v user-data]
    (let [node (swap-in swapper id)
          [n1 nk n2 lnbs lv] (t/insert* node k v user-data)
          sn1 (swappable-node swapper n1)
          sn2 (and n2 (swappable-node swapper n2))]
      #_(println "SWAPPABLE INSERT" (:id n1) (:id n2))
      [sn1 nk sn2 lnbs lv]))

  t/LookupableNode

  (lookup* [this k user-data]
    (t/lookup* (swap-in swapper id) k user-data))

  (lookup-range* [this k user-data]
    (t/lookup-range* (swap-in swapper id) k user-data)))


(defn swappable-node [swapper node]
  (let [id (t/id node)]
    (swap-out swapper id node)
    (map->SwappableNode {:swapper swapper
                         :id      id})))
