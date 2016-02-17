(ns clj-karabiner.relevancemap)


(defprotocol Relevancable
  (relevants [this])
  (irrelevants [this]))


(deftype RelevanceMap [hashkeys contents]

  clojure.lang.IPersistentMap
  (assoc [this key val]
    (RelevanceMap. hashkeys (.assoc contents key val)))
  (assocEx [this key val]
    (RelevanceMap. hashkeys (.assoc contents key val)))
  (without [this key]
    (RelevanceMap. hashkeys (.without contents key)))

  java.lang.Iterable
  (iterator [this]
    (.iterator contents))

  clojure.lang.Associative
  (containsKey [this key]
    (.containsKey contents key))
  (entryAt [this key]
    (.entryAt contents key))

  clojure.lang.IPersistentCollection
  (count [this]
    (.count contents))
  (cons [this o]
    (RelevanceMap. hashkeys (.cons contents o)))
  (empty [this]
    (RelevanceMap. #{} {}))
  (equiv [this o]
    (or (and (instance? RelevanceMap o)
             (.equiv (relevants this) (relevants o)))
        (and (instance? clojure.lang.IPersistentMap o)
             (.equiv (relevants this) o))))

  clojure.lang.Seqable
  (seq [this]
    (.seq contents))

  clojure.lang.ILookup
  (valAt [this key]
    (.valAt contents key))
  (valAt [this key not-found]
    (.valAt contents key not-found))

  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key))

  java.lang.Object
  (hashCode [this]
    (.hashCode (relevants this)))

  java.util.Map

  Relevancable
  (relevants [this]
    (into {} (filter #(contains? hashkeys (first %))
                     contents)))
  (irrelevants [this]
    (into {} (remove #(contains? hashkeys (first %))
                     contents))))

(defn new-relevancemap
  ([data relevant-keys]
   {:pre [(map? data)]}
   (->RelevanceMap relevant-keys data))

  ([data]
   (new-relevancemap data (set (keys data)))))



(comment (def tcm1 (->RelevanceMap #{:a} {:a 11 :b 22}))
         (def tcm2 (->RelevanceMap #{:a :c} {:a 11 :b 33 :c 44}))
         (def tcm3 (->RelevanceMap #{:a :c} {:a 11 :b 55 :c 44}))
         (println tcm1)
         (println tcm2)
         (relevants tcm1)
         (irrelevants tcm1)
         (relevants tcm2)
         (irrelevants tcm2)
         (tcm1 :a)
         (tcm1 :b)
         (tcm1 :c)
         (:a tcm1)
         (:b tcm1)
         (:c tcm1)
         (tcm2 :a)
         (tcm2 :b)
         (tcm2 :c)
         (:a tcm2)
         (:b tcm2)
         (:c tcm2)
         (= tcm1 tcm1)
         (= tcm1 {:a 11 :b 22})
         (= tcm1 {:a 11 :b 22 :c 33})
         (= tcm1 tcm2)
         (= tcm2 tcm1)
         (= tcm2 tcm2)
         (= tcm2 {:a 11 :b 33 :c 44})
         (= tcm2 {:a 11 :b 33})
         (= tcm2 tcm3)
         (merge tcm1 tcm2)
         (relevants (merge tcm1 tcm2))
         (merge tcm2 tcm1)
         (relevants (merge tcm2 tcm1))
         (hash tcm1)
         (hash tcm2)
         (hash tcm3)
         (into {} (map (fn [[k v]]
                         (println "key: " k " val: " v)
                         [k (inc v)])
                       tcm1)))
