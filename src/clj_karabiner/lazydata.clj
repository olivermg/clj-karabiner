(ns clj-karabiner.lazydata)


#_(defprotocol LazyData
  (realized? [this])
  (realize! [this]))


#_(defrecord Node [k l r])

(deftype LazyData [realize!-fn realized? data]

  clojure.lang.IDeref

  (deref [this]
    (dosync
     (when-not @realized?
       (ref-set data (realize!-fn))
       (ref-set realized? true)))
    @data)

  clojure.lang.IPending

  (isRealized [this]
    @realized?))

#_(defmethod print-method LazyData [o w]
  ((get-method print-method clojure.lang.IRecord) o w))


(defn lazy-data
  ([realize!-fn]
   (->LazyData realize!-fn (ref false) (ref nil)))
  ([realize!-fn data]
   (->LazyData realize!-fn (ref true) (ref data))))



;;;
;;; some sample invocations
;;;

#_(letfn [(realize!-fn [k]
          (println "FETCH" k)
          {:v (* k 100)
           :children {:left  (when (> k 0)
                               (lazy-data #(realize!-fn (dec k))))
                      :right (when (< k 10)
                               (lazy-data #(realize!-fn (inc k))))}})]
  (let [root (lazy-data #(realize!-fn 5))]
    (println "===")
    [(-> root
         deref
         (get-in [:children :left])
         deref
         :v)
     (-> root
         deref
         :v)
     (-> root
         deref
         (get-in [:children :right])
         deref
         :v)]))
