(ns clj-karabiner.external-storage
  (:refer-clojure :rename {load load-clj
                           key key-clj}))


(defprotocol ExternalStorage
  (load-data [this k])
  (save-data [this o]))

(defprotocol StorageBacked
  #_(load [this])
  #_(save [this])
  #_(saved-representation [this])

  (key [this])
  (data [this])
  (loaded-representation [this])
  (saved-representation [this]))

(defprotocol StoredData
  (load [this]))
