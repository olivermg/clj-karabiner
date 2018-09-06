(ns clj-karabiner.storage-backend
  (:refer-clojure :rename {load load-clj}))


(defprotocol LoadableStorageBackend
  (load-from-position [this position]))

(defn load [this]
  (load-from-position this nil))


(defprotocol AppendableStorageBackend
  (append [this obj]))
