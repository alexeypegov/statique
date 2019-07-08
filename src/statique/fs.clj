(ns statique.fs
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [statique.util :as util]
            [statique.logging :as log]
            [statique.cache :as cache]
            [me.raynes.fs :as fs]
            [pandect.algo.crc32 :as crc]))

(def ^:private markdown-ext ".md")
(def ^:private cache-ext    ".edn")
(def ^:private cache-name   (str "crc" cache-ext))

(defprotocol BlogFileSystem
  (info [this file])
  (copy-to-output [this src])
  (relative [this file])
  (note-files [this])
  (page-files [this])
  (output-dir [this])
  (theme-dir [this])
  (root-dir [this])
  (get-cache [this name])
  (get-instant-cache [this name])
  (close [this]))

(defrecord FileInfo [relative slug crc-mismatch src])
(defrecord Directories [root cache notes pages theme output])

(defn- slug
  [file]
  (let [name (.getName file)]
    (string/lower-case (subs name 0 (- (count name) (count markdown-ext))))))

(defn make-dirs
  [root cache notes pages theme output]
  (->Directories root cache notes pages theme output))

(defn- list-files
  [file-or-dir]
  (let [file (io/file file-or-dir)]
    (if (.isDirectory file)
      (filter #(not (.isDirectory %)) (file-seq file))
      (list file))))

(defn- file-info
  [root-dir cache file]
  (let [relative      (util/relative-path root-dir file)
        cached-crc    (.get cache relative 0)
        crc           (crc/crc32 file)
        crc-mismatch  (not= crc cached-crc)
        slug          (slug file)]
    (.put cache relative crc)
    (->FileInfo relative slug crc-mismatch file)))

(defn- copy-info-fn
  [root-dir cache src-dir dst-dir]
  (fn [file]
    (let [info (file-info root-dir cache file)]
      (assoc info
        :dst (io/file dst-dir (util/relative-path (.getParent src-dir) file))))))

(def ^:private copy
  (fn [{:keys [src dst crc-mismatch], :as info}]
    (let [not-exists (not (.exists dst))]
      (when (or not-exists crc-mismatch)
        (fs/copy+ src dst)
        info))))

(defn- make-copier
  [root-dir cache src-dir dst-dir]
  (comp
    copy
    (copy-info-fn root-dir cache src-dir dst-dir)))

(defn make-blog-fs
  [^Directories {:keys [root cache output notes pages theme], :as dirs}]
  (let [closeables  (atom '())
        crc-file    (io/file cache cache-name)
        crc-cache   (cache/file-cache crc-file)
        info-fn     (partial file-info root crc-cache)]
    (swap! closeables conj crc-cache)
    (reify BlogFileSystem
      (copy-to-output [_ src]
                      (let [src-file  (io/file root src)
                            copier    (make-copier root crc-cache src-file output)]
                        (->> (list-files src-file)
                             (map copier)
                             (filter some?)
                             count)))
      (info [_ file] (info-fn file))
      (get-cache [this name]
                 (let [cache (cache/file-cache (io/file cache (str name cache-ext)))]
                   (swap! closeables conj cache)
                   cache))
      (get-instant-cache [this name]
                         (let [cache (cache/file-cache (io/file cache (str name cache-ext)) :instant true)]
                           (swap! closeables conj cache)
                           cache))
      (relative [_ file] (util/relative-path root file))
      (output-dir [_] output)
      (root-dir [_] root)
      (theme-dir [_] theme)
      (note-files [this]
                  (->> (util/sorted-files notes :name-filter (util/postfix-filter markdown-ext))
                       reverse
                       (map info-fn)))
      (page-files [this]
                  (map info-fn (util/sorted-files pages :name-filter (util/postfix-filter markdown-ext))))
      (close [_]
             (doseq [cache @closeables]
               (.close cache))))))