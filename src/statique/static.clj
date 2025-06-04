(ns statique.static
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [statique.config :refer [get-generals]]
            [me.raynes.fs :as fs]))

(defn- copy-file
  [file dst-dir]
  (log/info "Copying" file "—>" (.getAbsolutePath dst-dir))
  (let [filename (fs/base-name file)]
    (fs/copy+ file (io/file dst-dir filename))))

(defn- copy-dir
  [dir dst-dir]
  (log/info "Copying" dir "—>" (.getAbsolutePath dst-dir))
  (fs/copy-dir dir dst-dir))

(defn copy
  [config]
  (let [[output-dir copy] (get-generals config :output-dir :copy)]
    (letfn [(do-copy [item] (when (fs/exists? item)
                              (if (fs/directory? item)
                                (copy-dir item output-dir)
                                (copy-file item output-dir))))]
      (dorun (map do-copy copy)))))
