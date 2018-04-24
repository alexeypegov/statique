(ns statique.stats
  (:require [statique.logging :as log]))

(defn all-tags
  [notes]
  (reverse
    (sort-by #(val %)
             (reduce (fn [res v]
                       (let [_v (symbol v)]
                         (assoc res _v (inc (get res _v 0)))))
                     {}
                     (into [] cat (map :tags notes))))))

(defn print-tag-stats
  [notes]
  (doall (map
           (comp
             log/info
             (fn [[t n]] (format "%-30s %d" t n)))
           (all-tags notes))))