(ns clj-karabiner.storage-backend.postgres
  (:require [clj-karabiner.storage-backend :as sb]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [com.amazonaws.services.dynamodbv2 AmazonDynamoDB AmazonDynamoDBClientBuilder]
           [com.amazonaws.auth
            DefaultAWSCredentialsProviderChain AWSStaticCredentialsProvider BasicAWSCredentials]
           [com.amazonaws.services.dynamodbv2.model
            AttributeValue GetItemRequest QueryRequest]))


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


(defrecord DynamoDbStorageBackend [tablename
                                   client]

  sb/LoadableStorageBackend

  (load-from-position [this position]
    (let [queryreq (doto (QueryRequest. tablename)
                     (.setKeyConditionExpression "#k1 = :v1 AND #k2 >= :v2")
                     (.setExpressionAttributeNames {"#k1" "entity"
                                                    "#k2" "position"})
                     (.setExpressionAttributeValues {":v1" (doto (AttributeValue.)
                                                             (.withS "fact"))
                                                     ":v2" (doto (AttributeValue.)
                                                             (.withN (str position)))}))]
      (some->> (.query client queryreq)
               (.getItems))))

  sb/AppendableStorageBackend

  (append [this obj]
    (.putItem client tablename obj)))


(defn dynamodb-storage-backend [tablename]
  (map->DynamoDbStorageBackend {:tablename tablename}))


(defn start [this]
  (let [credsprovider (DefaultAWSCredentialsProviderChain.)
        builder (AmazonDynamoDBClientBuilder/standard)]
    (merge this {:client (-> builder
                             (.withCredentials credsprovider)
                             (.build))})))


(defn stop [{:keys [client] :as this}]
  (.close client)
  (merge this {:client nil}))
