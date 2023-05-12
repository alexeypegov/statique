(ns statique.static
  (:require [clojure.java.io :as io]
            [statique.config :refer [with-general]]
            [me.raynes.fs :as fs]
            #_[clojure.tools.logging :as log :refer [log warn error info]]))

(defn- copy-file
  [file dst-dir]
  (println (format "Copying \"%s\"..." file))
  (let [filename (fs/base-name file)]
    (fs/copy+ file (io/file dst-dir filename))))

(defn- copy-dir
  [dir dst-dir]
  (println (format "Copying \"%s\"..." dir))
  (fs/copy-dir dir dst-dir))

(defn copy
  [config]
  (let [output-dir (with-general config :output-dir)
        copy       (with-general config :copy)]
    (letfn [(do-copy [item] (when (fs/exists? item)
                              (if (fs/directory? item)
                                (copy-dir item output-dir)
                                (copy-file item output-dir))))]
      (dorun (map do-copy copy)))))
