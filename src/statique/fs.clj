(ns statique.fs
  (:require [clojure.java.io :as io]
            [statique.util :as util]
            [statique.logging :as log]
            [statique.cache :as cache]
            [me.raynes.fs :as fs]))

(def ^:private markdown-ext ".md")
(def ^:private cache-ext    ".edn")
(def ^:private cache-name   (str "timestamp" cache-ext))

(defprotocol BlogFileSystem
  (info [this file])
  (copy [this src dst-dir])
  (relative [this file])
  (note-files [this])
  (output-dir [this])
  (cache [this name])
  (close [this]))

(defrecord FileInfo [relative timestamp cached-timestamp src])
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
  [root-dir cache file]
  (let [relative  (util/relative-path root-dir file)
        cached-ts (.get cache relative 0)
        timestamp (.lastModified file)]
    (.put cache relative timestamp)
    (->FileInfo relative timestamp cached-ts file)))

(defn- copy-info-fn
  [root-dir cache src-dir dst-dir]
  (fn [file]
    (assoc (file-info root-dir file cache)
      :dst (io/file dst-dir (util/relative-path (.getParent src-dir) file)))))

(def copy
  (fn [{:keys [src dst timestamp cached-timestamp], :as info}]
    (let [not-exists  (not (.exists dst))
          ts-mismatch (not= timestamp cached-timestamp)]
      (when (or not-exists ts-mismatch)
        (fs/copy+ src dst)
        info))))

(defn- make-copier
  [root-dir cache src-dir dst-dir]
  (comp
    copy
    (copy-info-fn root-dir cache src-dir dst-dir)))

(defn make-blog-fs
  [^Directories {:keys [root cache output notes], :as dirs}]
  (let [closeables        (atom '())
        timestamps-file   (io/file cache cache-name)
        timestamps-cache  (cache/file-cache timestamps-file)
        info-fn           (partial file-info root timestamps-cache)]
    (swap! closeables conj timestamps-cache)
    (reify BlogFileSystem
      (copy [_ src-dir dst-dir]
            (let [copier (make-copier root timestamps-cache src-dir dst-dir)]
              (filter some?
                  (map copier (list-files src-dir)))))
      (info [_ file]
            (info-fn file))
      (cache [this name]
             (let [cache (cache/file-cache (io/file cache name))]
               (swap! closeables conj cache)
               cache))
      (relative [_ file]
                (util/relative-path root file))
      (output-dir [_]
                  output)
      (note-files [this]
                  (map info-fn (reverse (util/sorted-files notes markdown-ext))))
      (close [_]
             (doseq [cache @closeables]
               (.close cache))))))