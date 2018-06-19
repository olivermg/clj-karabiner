(ns clj-karabiner.tree.serialization
  (:require [clj-karabiner.tree :as t]
            [clj-karabiner.tree.bplustree :as bt]
            [clj-karabiner.tree.bplustree-proxy :as bp]
            [clj-karabiner.external-memory :as em])
  (:import [clj_karabiner.tree.bplustree B+TreeInternalNode B+TreeLeafNode]
           [clj_karabiner.tree.bplustree_proxy B+TreeInternalNodeProxy B+TreeLeafNodeProxy]))


(extend-type B+TreeInternalNode

  em/ExternalMemoryBacked

  (storage-key [this]
    (str "b+in:" (-> this (select-keys #{:b :size :ks :vs}) hash)))

  em/ExternalMemoryBackedReal

  (storage-data [{:keys [b size ks vs] :as this}]
    {:b b
     :size size
     :ks ks
     :vs-proxies (map em/proxy-obj vs)})

  (proxy-obj [this]
    (bp/->B+TreeInternalNodeProxy (em/storage-key this))))


(extend-type B+TreeLeafNode

  em/ExternalMemoryBacked

  (storage-key [this]
    (str "b+ln:" (-> this (select-keys #{:b :size :m}) hash)))

  em/ExternalMemoryBackedReal

  (storage-data [{:keys [b size m] :as this}]
    {:b b
     :size size
     :m m})

  (proxy-obj [this]
    (bp/->B+TreeLeafNodeProxy (em/storage-key this))))


(extend-type B+TreeInternalNodeProxy

  em/ExternalMemoryBacked

  (storage-key [{:keys [key] :as this}]
    key)

  em/ExternalMemoryBackedProxy

  (real-obj [this {:keys [b size ks vs-proxies] :as data}]
    (bt/->B+TreeInternalNode b size ks vs-proxies)))


(extend-type B+TreeLeafNodeProxy

  em/ExternalMemoryBacked

  (storage-key [{:keys [key] :as this}]
    key)

  em/ExternalMemoryBackedProxy

  (real-obj [this {:keys [b size m] :as data}]
    (bt/->B+TreeLeafNode b size m)))



;;;
;;; sample invocations
;;;

#_(let [ms (clj-karabiner.external-memory.atom/atom-storage {:readers bp/edn-readers})
      mem  (em/external-memory ms)
      ln1  (bt/->B+TreeLeafNode 3 1 {[:a 1] 11})
      ln2  (bt/->B+TreeLeafNode 3 2 {[:a 2] 22
                                     [:a 3] 33})
      in1  (bt/->B+TreeInternalNode 3 2 [[:a 1]] [ln1 ln2])
      ln1p (em/save mem ln1)
      ln2p (em/save mem ln2)
      in1p (em/save mem in1)
      ln1r (em/load mem ln1p)
      ln2r (em/load mem ln2p)
      in1r (em/load mem in1p)]
  [(= ln1 ln1r) (= ln2 ln2r) (= in1 in1r)]
  (let [[_ n] (t/lookup in1r [:a 1])]
    (em/load mem n)))
