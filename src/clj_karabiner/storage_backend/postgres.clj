(ns clj-karabiner.storage-backend.postgres
  (:require [clj-karabiner.storage-backend :as sb]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util Properties]
           [java.sql DriverManager]))


#_(defn- query [{:keys [connection] :as this} sql & kvmap]
  (let [s (.prepareStatement connection)]
    (for [[k v] kvmap]
      (condp #(instance? %1 %2) v
        Long           (.setLong s v)
        Double         (.setDouble s v)
        String         (.setString s v)
        java.util.Date (.setDate s v)))))


(defn- to-transit [v]
  (let [out (ByteArrayOutputStream. 10)
        w (t/writer out :json)]
    (t/write w v)
    (.toByteArray out)))


(defn- from-transit [tv]
  (let [in (ByteArrayInputStream. tv)
        r (t/reader in :json)]
    (t/read r)))


(defrecord PostgresStorageBackend [jdbc-url user password tablename ssl?
                                   connection]

  sb/LoadableStorageBackend

  (load-from-position [this position]
    (let [sql (str "select * from \"" tablename "\" where position >= ?")
          stmt (doto (.preparedStatement connection)
                 (.setLong 1 position))
          dbresult (.executeQuery stmt sql)
          result (transient [])]
      (loop [row (.next dbresult)]
        (if row
          (do (conj! result row)
              (recur (.next dbresult)))
          (persistent! result)))))

  sb/AppendableStorageBackend

  (append [{:keys [tablename] :as this} obj]
    (let [sql (str "insert into \"" tablename "\" (fact) values (?)")
          stmt (doto (.preparedStatement connection)
                 (.setString 1 (to-transit obj)))]
      (.executeInsert stmt sql))))


(defn postgres-storage-backend [jdbc-url user password & {:keys [tablename ssl?]
                                                          :or {ssl? true}}]
  (map->PostgresStorageBackend {:jdbc-url jdbc-url
                                :user user
                                :password password
                                :tablename (or tablename "facts")
                                :ssl? ssl?}))


(defn start [{:keys [jdbc-url user password ssl?] :as this}]
  (let [props (doto (Properties.)
                (.setProperty "user" user)
                (.setProperty "password" password)
                (.setProperty "ssl" (if ssl? "true" "false")))
        connection (DriverManager/getConnection jdbc-url props)]
    (merge this {:connection connection})))


(defn stop [{:keys [connection] :as this}]
  (.close connection)
  (merge this {:connection nil}))



;;;
;;; sample invocations
;;;

(let [be (postgres-storage-backend "jdbc:postgresql://localhost/factstore"
                                   "oliver" "oliver")])
