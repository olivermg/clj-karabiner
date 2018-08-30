(defproject clj-karabiner "0.1.1-SNAPSHOT"
  :description "Useful custom data structures"
  :url "http://github.com/olivermg/clj-karabiner"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 #_[org.clojure/core.async "0.4.474"
                  ;;;:exclusions [org.clojure/tools.reader]
                  ]
                 [digest "1.4.8"]
                 #_[com.taoensso/nippy "2.14.0"]
                 [com.cognitect/transit-clj "0.8.309"
                  ;;;:exclusions [com.fasterxml.jackson.core/jackson-core]
                  ]
                 [com.taoensso/nippy "2.14.0"]

                 [com.taoensso/carmine "2.18.1"]
                 #_[spootnik/kinsky "0.1.22"]
                 [org.apache.kafka/kafka-clients "2.0.0"]]

  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [criterium "0.4.4"]]}})
