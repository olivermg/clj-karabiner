(ns clj-karabiner.kvstore.redis
  (:require [clj-karabiner.kvstore :as kvs]
            [taoensso.carmine :as car]))


(defrecord RedisKvStore [conn clj->store store->clj]

  kvs/KvStore

  (store [this k v]
    (car/wcar conn
              (car/set k v)))

  (lookup* [this k not-found]
    ;;; TODO: check on not-found:
    (car/wcar conn
              (car/get k))))


(defn redis-kvstore [uri]
  (map->RedisKvStore {:conn {:pool {}
                             :spec {:uri uri}}}))


(defn start [this]
  this)


(defn stop [this]
  this)
