(ns statique.static
  (:require [clojure.java.io :as io]
            [statique.config :as cfg]
            [me.raynes.fs :as fs])
  (:use [clojure.tools.logging :only [log warn error info]]))

(defn- copy-file [file dst-dir]
  (println (format "Copying \"%s\"..." file))
  (let [filename (fs/base-name file)]
    (fs/copy+ file (io/file dst-dir filename))))

(defn- copy-dir [dir dst-dir]
  (println (format "Copying \"%s\"..." dir))
  (fs/copy-dir dir dst-dir))

(defn copy []
  (let [output-dir (cfg/general :output-dir)
        copy (cfg/general :copy)]
  (letfn [(do-copy [item] (when (fs/exists? item)
                            (if (fs/directory? item)
                              (copy-dir item output-dir)
                              (copy-file item output-dir))))]
    (dorun (map do-copy copy)))))
