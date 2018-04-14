(ns statique.logging)

(def levels {:debug 0 :info 1 :warning 2 :error 3})
(def ^:private ^:dynamic *level* (:info levels))
(def ^:private ^:dynamic *colored* true)

(defn set-level
  [level]
  {:pre [(number? level) (and (>= level 0) (<= level (:error levels)))]}
  (set! *level* level))

(defn log
  [& s]
  (println (clojure.string/join " " s)))

(defn- leveled
  [l & s]
  (when (>= *level* (get levels l))
    (apply log s)))

(defn info [& s] (apply leveled :info s))
(defn warn [& s] (apply leveled :warn s))
(defn error [& s] (apply leveled :error s))
(defn debug [& s] (apply leveled :debug s))