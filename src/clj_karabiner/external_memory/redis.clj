(ns clj-karabiner.external-memory.redis
  (:require [clj-karabiner.external-memory :as em]
            [taoensso.carmine :as car]))


(defrecord RedisExternalMemory [conn]

  em/ExternalMemory

  (load* [this k]
    (car/wcar conn
              (car/get k)))

  (save* [this k d]
    (car/wcar conn
              (car/set k d))))


(defn redis-external-memory [uri]
  (->RedisExternalMemory {:pool {}
                          :spec {:uri uri}}))
