(ns statique.fs
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.logging :as log]
            [statique.cache :as c]
            [me.raynes.fs :as fs]))

(def ^:private cache-ext ".edn")
(def ^:private cache-name (str "timestamp" cache-ext))

(defprotocol BlogFileSystem
  (copy [this src dst-dir])
  (info [this file])
  (make-cache [this name] [this name producer-fn])
  (relative [this file])
  (save [this]))

(defn- list-files
  [file-or-dir]
  (let [file (io/file file-or-dir)]
    (if (.isDirectory file)
      (filter #(not (.isDirectory %)) (file-seq file))
      (list file))))

(defn- file-info
  [root-dir file cache]
  (let [relative  (u/relative-path root-dir file)
        cached-ts (.read-v cache relative 0)
        timestamp (.lastModified file)]
    (.write-v cache relative timestamp)
    (assoc {}
      :relative         relative
      :timestamp        timestamp
      :cached-timestamp cached-ts
      :src              file)))

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

(defn make-fs
  [root-dir cache-dir]
  (let [cache-file  (io/file cache-dir cache-name)
        cache       (c/make-file-cache cache-file)]
    (reify BlogFileSystem
      (copy [_ src dst-dir]
            (let [copier (make-copier root-dir src dst-dir cache)]
              (count
                (filter #(not= % nil)
                  (map copier (list-files src))))))
      (info [_ file]
            (file-info root-dir file cache))
      (make-cache [_ name]
                  (u/make-file-cache (io/file cache-dir (str name cache-ext))))
      (make-cache [_ name producer-fn]
                  (u/make-file-cache (io/file cache-dir (str name cache-ext)) :producer-fn producer-fn))
      (relative [_ file]
                (u/relative-path root-dir file))
      (save [_]
            (.save cache)))))