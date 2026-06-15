(ns statique.static
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [statique.config :refer [get-generals]]
            [statique.util :as u]
            [me.raynes.fs :as fs]))

(defn- copy-if-changed
  [src dst]
  (let [src (io/file src)
        dst (io/file dst)]
    (when (or (not (fs/exists? dst))
              (not= (u/crc32 src) (u/crc32 dst)))
      (log/info "Copying" (.getPath src) "→" (.getPath dst))
      (fs/copy+ src dst))))

(defn- copy-file
  [file dst-dir]
  (let [src (io/file file)]
    (if (fs/exists? src)
      (copy-if-changed src (io/file dst-dir (fs/base-name src)))
      (log/error "Static source file not found:" (.getPath src)))))

(defn- copy-dir
  [src-dir dst-dir]
  (let [src      (io/file src-dir)
        src-path (.toPath src)
        dst-base (io/file dst-dir (fs/base-name src))
        dst-path (.toPath dst-base)]
    (when (fs/exists? dst-base)
      (doseq [f (filter fs/file? (file-seq dst-base))]
        (let [src-file (io/file src (str (.relativize dst-path (.toPath f))))]
          (when-not (fs/exists? src-file)
            (log/info "Removing" (.getPath f))
            (fs/delete f)))))
    (doseq [f (filter fs/file? (file-seq src))]
      (copy-if-changed f (io/file dst-base (str (.relativize src-path (.toPath f))))))))

(defn copy
  [config]
  (let [[output-dir copy] (get-generals config :output-dir :copy)]
    (dorun (map #(if (fs/directory? %)
                   (copy-dir % output-dir)
                   (copy-file % output-dir))
                copy))))
