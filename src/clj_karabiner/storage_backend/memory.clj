(ns clj-karabiner.storage-backend.memory
  (:require [clj-karabiner.storage-backend :as sb]))


;;; TODO: redesign this concept so that it can handle
;;;   objects stored by other processes. ie implement
;;;   some kind of ongoing stream instead of a single
;;;   invocation of load:

(defrecord MemoryStorageBackend [storage]

  sb/LoadableStorageBackend

  (load-from-position [this position]
    (drop (or position 0) @storage))

  sb/AppendableStorageBackend

  (append [this obj]
    (swap! storage #(conj % obj))))


(defn memory-storage-backend

  ([]
   (map->MemoryStorageBackend {:storage (atom [])}))

  ([content]
   (map->MemoryStorageBackend {:storage (atom content)})))
