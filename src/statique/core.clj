(ns statique.core
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.notes :as n]
            [statique.static :as s]
            [statique.context :as ctx])
  (:gen-class))

(def ^:private working-dir (u/working-dir))

(defn- blog-dir? [path]
  (let [file (io/as-file path)]
    (and
      (.isDirectory file)
      (.exists (io/file path ctx/config-name)))))

(defn -main [& args]
  (printf "Statique %s\n\n" ctx/app-version)
  (if (blog-dir? working-dir)
    (do
      (n/generate)
      (n/generate-singles)
      (s/copy))
    (printf "Unable to find config file (%s) in \"%s\"\n" ctx/config-name working-dir)))
