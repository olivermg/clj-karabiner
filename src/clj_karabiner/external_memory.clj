(ns clj-karabiner.external-memory
  (:refer-clojure :rename {load load-clj
                           key key-clj}))


#_(defprotocol ExternalMemoryBackedInstance
  (key [this]))


#_(defprotocol ExternalMemoryBacked
  #_(load [this])
  #_(save [this])
  #_(saved-representation [this])

  (key [this])
  (data [this])
  (instantiate [this])
  (proxy-representation [this]))


(defprotocol MemoryStorage
  (load* [this k])
  (save* [this k d]))


(defrecord ExternalMemory [memory-storage serializers deserializers proxy-creators])

(defn load [this proxy-obj]
  (let [{:keys [memory-storage deserializers]} this
        {:keys [key-fn real-obj-fn]} (get deserializers (type proxy-obj))
        k (key-fn proxy-obj)
        d (load* memory-storage k)
        real-obj (real-obj-fn d)]
    real-obj))

(defn save [this real-obj]
  (let [{:keys [memory-storage serializers proxy-creators]} this
        to (type real-obj)
        {:keys [key-fn data-fn]} (get serializers to)
        k (key-fn real-obj)
        d (data-fn real-obj)
        proxy-creator (get proxy-creators to)
        proxy (proxy-creator real-obj)]
    (save* memory-storage k d)
    proxy))

(defn external-memory [memory-storage serializers deserializers proxy-creators]
  (->ExternalMemory memory-storage serializers deserializers proxy-creators))



#_(do (defrecord Foo [id child temp])
    (defrecord FooProxy [k])

    (let [am      (clj-karabiner.external-memory.atom/atom-storage)
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
          em  (external-memory am sers desers proxycs)
          f2  (->Foo 22 nil :temp-22)
          f1  (->Foo 11 f2  :temp-11)
          f2p (save em f2)
          f1p (save em f1)
          f1r (load em f1p)
          f2r (load em (:child f1r))]
      (-> [am f1 f1p f1r]
          clojure.pprint/pprint)
      [f1r f2r]))
