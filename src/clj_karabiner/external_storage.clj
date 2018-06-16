(ns clj-karabiner.external-storage
  (:refer-clojure :rename {load load-clj}))


(defprotocol ExternalStorage
  (load-data [this k])
  (save-data [this k data]))

(defprotocol StorageBacked
  (load [this])
  (save [this]))
