(ns statique.fs
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.logging :as log]
            [me.raynes.fs :as fs])
  (:import [java.nio.file Path Paths]))

(def ^:private cache-name "timestamp.edn")

(defn- relative
  [root file]
  (.toString (.relativize
               (.toPath (io/as-file root))
               (.toPath file))))

(defn- file-processor
  [root read-cache write-cache]
  (fn [file]
    (let [key               (.hashCode (relative root file))
          cached-modified   (get read-cache key 0)
          last-modified     (.lastModified file)]
      (swap! write-cache assoc key last-modified)
      {:file file
       :changed (not= last-modified cached-modified)})))

(defprotocol BlogFileSystem
  (copy [this src dst-dir])
  (save [this]))

(defn- list-files
  [processor path]
  (let [file (io/as-file path)]
    (if (.isDirectory file)
      (map processor (filter #(.isFile %) (file-seq path)))
      (list (processor file)))))

(defn- copier
  [src dst]
  (fn [fm]
    (let [file (:file fm)
          dst-file (io/file dst (relative (.getParent src) file))]
      (fs/copy+ file dst-file))))

(defn make-fs
  [root-path cache-path]
  (let [cache       (io/file cache-path cache-name)
        root        (io/as-file root-path)
        read-cache  (u/read-edn cache)
        write-cache (atom {})
        processor   (file-processor root read-cache write-cache)]
      (reify BlogFileSystem
        (copy [_ src dst-dir]
              (let [copier (copier src dst-dir)]
                (count
                  (map copier
                       (filter #(get % :changed true)
                               (list-files processor src))))))
        (save [_]
              (.mkdirs (.getParentFile cache))
              (spit cache (with-out-str (pr @write-cache)))
              (log/debug (count @write-cache) "file entries were written to" (.getPath cache))))))