(ns clj-karabiner.external-memory
  (:refer-clojure :rename {load load-clj}))


(defprotocol ExternalMemoryBacked
  (storage-key [this]))

(defprotocol ExternalMemoryBackedReal
  (storage-data [this])
  (proxy-obj [this]))

(defprotocol ExternalMemoryBackedProxy
  (real-obj [this data]))


(defprotocol ExternalMemory
  (load* [this k])
  (save* [this k d]))


(defn load [this pobj]
  (if (satisfies? ExternalMemoryBackedProxy pobj)
    (let [k (storage-key pobj)
          d (load* this k)]
      (real-obj pobj d))
    pobj))

(defn save [this robj]
  (if (satisfies? ExternalMemoryBackedReal robj)
    (let [k (storage-key robj)
          d (storage-data robj)]
      (save* this k d)
      (proxy-obj robj))
    robj))



;;;
;;; sample invocations
;;;

#_(do (defrecord Foo [id child temp])
    (defrecord FooProxy [k])

    (let [em      (clj-karabiner.external-memory.atom/atom-external-memory)
          sers    {Foo {:key-fn  :id
                        :data-fn (fn [{:keys [id child]}]
                                   {:id id
                                    :child (:id child)})}}
          desers  {FooProxy {:key-fn      :k
                             :real-obj-fn (fn [{:keys [id child]}]
                                            (->Foo id (when-not (nil? child)
                                                        (->FooProxy child))
                                                   nil))}}
          proxycs {Foo (fn [{:keys [id]}]
                         (->FooProxy id))}
          f2  (->Foo 22 nil :temp-22)
          f1  (->Foo 11 f2  :temp-11)
          f2p (save em f2)
          f1p (save em f1)
          f1r (load em f1p)
          f2r (load em (:child f1r))]
      (-> [em f1 f1p f1r]
          clojure.pprint/pprint)
      [f1r f2r]))
