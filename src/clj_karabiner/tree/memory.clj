(ns clj-karabiner.tree.memory
  (:require [clj-karabiner.tree :as t]))


(defrecord MemoryBackend [storage]

  t/StorageBackend

  (load-data [this k]
    (get @storage k))

  (save-data [this k data]
    (swap! storage #(assoc % k data))))


(defn memory-backend []
  (->MemoryBackend (atom {})))


(defrecord StoredNode [storage-key storage-backend]

  t/TreeModifyable

  (insert [this k v]
    (t/insert (t/load this) k v))

  t/TreeLookupable

  (lookup [this k]
    (t/lookup (t/load this) k))

  (lookup-range* [this k]
    (t/lookup-range (t/load this) k))

  t/StorageBacked

  (load [this]
    (t/load-data storage-backend storage-key))

  (save [this]
    this))
