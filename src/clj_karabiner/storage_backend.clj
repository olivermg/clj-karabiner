(ns clj-karabiner.storage-backend
  (:refer-clojure :rename {load load-clj}))


(defprotocol LoadableStorageBackend
  (load [this]))

(defprotocol AppendableStorageBackend
  (append [this obj]))
