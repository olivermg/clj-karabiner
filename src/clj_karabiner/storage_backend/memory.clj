(ns clj-karabiner.storage-backend.memory
  (:require [clj-karabiner.storage-backend :as sb]))


(defrecord MemoryStorageBackend [storage]

  sb/LoadableStorageBackend

  (load [this]
    (lazy-seq @storage))

  sb/AppendableStorageBackend

  (append [this obj]
    (swap! storage #(conj % obj))))


(defn memory-storage-backend

  ([]
   (map->MemoryStorageBackend {:storage (atom [])}))

  ([content]
   (map->MemoryStorageBackend {:storage (atom content)})))
