(ns statique.logging)

(def levels {:debug 0 :info 1 :warning 2 :error 3})
(def ^:private ^:dynamic *level* (:info levels))
(def ^:private ^:dynamic *colored* true)

(defn log
  [& s]
  (println (clojure.string/join " " s)))

(defn- leveled
  [l & s]
  (when (>= (get levels l) *level*)
    (apply log s)))

(defn info [& s] (apply leveled :info s))
(defn warn [& s] (apply leveled :warn s))
(defn error [& s] (apply leveled :error s))
(defn debug [& s] (apply leveled :debug s))

(defn set-level
  [level]
  {:pre [(keyword? level)]}
  (binding [*level* *level*]
    (set! *level* (level levels))
    (debug (format "'%s' logging enabled" (name level)))))