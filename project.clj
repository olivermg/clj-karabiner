(defproject clj-karabiner "0.1.2-SNAPSHOT"
  :description "Useful custom data structures"
  :url "http://github.com/olivermg/clj-karabiner"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 #_[org.clojure/core.async "0.4.474"
                  ;;;:exclusions [org.clojure/tools.reader]
                  ]
                 [org.clojure/tools.logging "0.4.1"]
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
                                  [criterium "0.4.4"]
                                  [com.clojure-goes-fast/clj-memory-meter "0.1.2"]
                                  [org.apache.logging.log4j/log4j-api "2.11.1"]
                                  [org.apache.logging.log4j/log4j-core "2.11.1"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user}
                   :java-opts ["-Dlog4j.configurationFile=./resources/log4j2-dev.properties"]}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  #_["deploy"]
                  #_["clean"]
                  #_["uberjar"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  #_["vcs" "push"]])
