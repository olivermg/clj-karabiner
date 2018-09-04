(ns clj-karabiner.kvstore.redis
  (:require [clj-karabiner.kvstore :as kvs]
            [taoensso.carmine :as car]))


(defrecord RedisKvStore [conn clj->store store->clj]

  kvs/KvStore

  (store [this k v]
    (let [v ((or clj->store identity) v)]
      (car/wcar conn
                (car/set k v))))

  (lookup* [this k not-found]
    ;;; TODO: check on not-found:
    (-> (car/wcar conn
                  (car/get k))
        ((or store->clj identity)))))


(defn redis-kvstore [uri & {:keys [clj->store store->clj]}]
  (map->RedisKvStore {:conn {:pool {}
                             :spec {:uri uri}}
                      :clj->store clj->store
                      :store->clj store->clj}))
