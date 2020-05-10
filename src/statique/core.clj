(ns statique.core
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.notes :as n]
            [statique.static :as s]
            [statique.config :as cfg])
  (:gen-class))

(def ^:private working-dir (u/working-dir))

(defn- blog-dir? [path]
  (let [file (io/as-file path)]
    (and
      (.isDirectory file)
      (.exists (io/file path cfg/config-name)))))

(defn -main [& args]
  (printf "Statique %s\n\n" cfg/app-version)
  (if (blog-dir? working-dir)
    (do
      (n/generate-notes)
      (n/generate-singles)
      (s/copy))
    (printf "Unable to find config file (%s) in \"%s\"\n" cfg/config-name working-dir)))
