(ns user
  (:require [clojure.tools.logging :as log]
            [clojure.tools.logging.impl :as logi]))

(alter-var-root #'log/*logger-factory*
                (fn [_]
                  (logi/log4j2-factory)))
