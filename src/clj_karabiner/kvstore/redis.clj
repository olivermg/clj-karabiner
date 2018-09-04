(ns clj-karabiner.kvstore.redis
  (:require [clj-karabiner.kvstore :as kvs]
            [taoensso.carmine :as car]))


(defrecord RedisKvStore [conn]

  kvs/KvStore

  (store* [this k v pre-process]
    (let [v ((or pre-process identity) v)]
      (car/wcar conn
                (car/set k v))))

  (lookup* [this k not-found post-process]
    ;;; TODO: check on not-found:
    (-> (car/wcar conn
                  (car/get k))
        ((or post-process identity)))))


(defn redis-kvstore [uri]
  (map->RedisKvStore {:conn {:pool {}
                             :spec {:uri uri}}}))
