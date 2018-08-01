(ns clj-karabiner.storage-backend)


(defprotocol LoadableStorageBackend
  (load [this f]))

(defprotocol AppendableStorageBackend
  (append [this obj]))
