(ns statique.static
  (:require [clojure.java.io :as io]
            [statique.context :as ctx]
            [me.raynes.fs :as fs])
  (:use [clojure.tools.logging :only [log warn error info]]))

(defn- copy-file [file dst-dir]
  (info (format "Copying \"%s\"..." file))
  (let [filename (fs/base-name file)]
    (fs/copy+ file (io/file dst-dir filename))))

(defn- copy-dir [dir dst-dir]
  (info (format "Copying \"%s\"..." dir))
  (fs/copy-dir dir dst-dir))

(defn copy []
  (letfn [(do-copy [item] (when (fs/exists? item)
                            (if (fs/directory? item)
                              (copy-dir item ctx/output-dir)
                              (copy-file item ctx/output-dir))))]
    (dorun (map do-copy ctx/copy))))
