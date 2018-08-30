(ns clj-karabiner.stats)


(def +stats+ (atom {:nodes {:internal 0
                            :leaf 0}
                    :lookups {:internal 0
                              :leaf 0
                              :local 0}
                    :inserts {:internal 0
                              :leaf 0}
                    :compares 0}))
