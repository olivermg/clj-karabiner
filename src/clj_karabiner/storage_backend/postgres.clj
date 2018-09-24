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


(defn- to-transitstring [v]
  (let [out (ByteArrayOutputStream. 10)
        w (t/writer out :json)]
    (t/write w v)
    (-> (.toByteArray out)
        (String. "UTF-8"))))


(defn- from-transitstring [tv]
  (let [in (-> (.getBytes tv "UTF-8")
               (ByteArrayInputStream.))
        r (t/reader in :json)]
    (t/read r)))


(defrecord PostgresStorageBackend [jdbc-url user password tablename ssl?
                                   connection]

  sb/LoadableStorageBackend

  ;;; TODO: implement this via lazy loading (lazy-seq, see kafka implementation for reference):
  (load-from-position [this position]
    (let [sql (str "select fact from \"" tablename "\" where position >= ? order by position asc")
          stmt (doto (.prepareStatement connection sql)
                 (.setLong 1 position))
          resultset (.executeQuery stmt)
          result (transient [])]
      (loop [success? (.next resultset)]
        (if success?
          (do (conj! result (-> resultset
                                (.getString 1)
                                (from-transitstring)))
              (recur (.next resultset)))
          (persistent! result)))))

  sb/AppendableStorageBackend

  (append [{:keys [tablename] :as this} obj]
    (let [sql (str "insert into \"" tablename "\" (fact) values (?)")
          stmt (doto (.prepareStatement connection sql)
                 (.setString 1 (to-transitstring obj)))]
      (.execute stmt))))


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

#_(let [be (-> (postgres-storage-backend "jdbc:postgresql://localhost/factstore"
                                       "factstore" "factstore"
                                       :tablename "facts" :ssl? false)
             start)]
  (sb/append be [:x (rand-int 1000)])
  (sb/load-from-position be 0))
