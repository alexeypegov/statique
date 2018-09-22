(ns statique.fs
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.logging :as log]
            [statique.cache :as c]
            [me.raynes.fs :as fs]))

(def ^:private markdown-ext   ".md")
(def ^:private cache-ext      ".edn")
(def ^:private cache-name     (str "timestamp" cache-ext))

(defprotocol BlogFileSystem
  (info [this file])
  (copy [this src dst-dir])
  (relative [this file])
  (notes [this])
  (output-dir [this])
  (make-cache [this name] [this name producer-fn])
  (close [this]))

(defrecord NoteInfo [relative timestamp cached-timestamp src])
(defrecord Directories [root cache notes output])

(defn make-dirs
  [root cache notes output]
  (->Directories root cache notes output))

(defn- list-files
  [file-or-dir]
  (let [file (io/file file-or-dir)]
    (if (.isDirectory file)
      (filter #(not (.isDirectory %)) (file-seq file))
      (list file))))

(defn- file-info
  [root-dir file cache]
  (let [relative  (u/relative-path root-dir file)
        cached-ts (.get cache relative 0)
        timestamp (.lastModified file)]
    (.put cache relative timestamp)
    (NoteInfo. relative timestamp cached-ts file)))

(defn- copy-info-fn
  [root-dir src dst cache]
  (fn [file]
    (assoc (file-info root-dir file cache)
      :dst (io/file dst (u/relative-path (.getParent src) file)))))

(def copy
  (fn [m]
    (let [exists      (.exists (:dst m))
          ts-mismatch (not= (:timestamp m) (:cached-timestamp m))]
      (if (or (not exists) ts-mismatch)
        (do
          (fs/copy+ (:src m) (:dst m))
          m)
        nil))))

(defn- make-copier
  [root-dir src dst cache]
  (comp
    copy
    (copy-info-fn root-dir src dst cache)))

(defn make-blog-fs
  [^Directories dirs]
  (let [cache-file  (io/file (:cache dirs) cache-name)
        cache       (c/make-file-cache cache-file)]
    (reify BlogFileSystem
      (copy [_ src dst-dir]
            (let [copier (make-copier (:root dirs) src dst-dir cache)]
              (filter #(not= % nil)
                  (map copier (list-files src)))))
      (info [_ file]
            (file-info (:root dirs) file cache))
      (make-cache [_ name]
                  (c/make-file-cache (io/file (:cache dirs) (str name cache-ext))))
      (make-cache [_ name producer-fn]
                  (c/make-file-cache (io/file (:cache dirs) (str name cache-ext)) :producer-fn producer-fn))
      (relative [_ file]
                (u/relative-path (:root dirs) file))
      (output-dir [_]
                  (:output dirs))
      (notes [this]
             (map
               #(.info this %)
               (reverse
                 (u/sorted-files (:notes dirs) markdown-ext))))
      (close [_]
            (.close cache)))))