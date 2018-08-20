(ns statique.fs
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.logging :as log]
            [me.raynes.fs :as fs]))

(def ^:private cache-name "timestamp.edn")

(defprotocol BlogFileSystem
  (copy [this src dst-dir])
  (info [this file])
  (save [this]))

(defn- list-files
  [file-or-dir]
  (let [file (io/file file-or-dir)]
    (if (.isDirectory file)
      (filter #(not (.isDirectory %)) (file-seq file))
      (list file))))

(defn- file-info
  [root-dir file cache]
  (let [relative  (u/relative-path root-dir file)]
    (assoc {}
      :relative relative
      :timestamp (.lastModified file)
      :cached-timestamp (get (:read cache) relative 0)
      :src file)))

(defn- copy-info-fn
  [root-dir src dst cache]
  (fn [file]
    (let [info      (file-info root-dir file cache)
          dst-file  (io/file dst (u/relative-path (.getParent src) file))
          exists    (.exists dst-file)]
      (assoc info
        :exists exists
        :outdated (or (not exists) (not= (:timestamp info) (:cached-timestamp info)))
        :dst dst-file))))

(def copy
  (fn [m]
    (if (:outdated m)
      (fs/copy+ (:src m) (:dst m)))
    m))

(defn- update-cache-fn
  [cache]
  (fn [m]
    (swap! (:write cache) assoc (:relative m) (:timestamp m))
    m))

(defn- make-copier
  [root-dir src dst cache]
  (comp
    (update-cache-fn cache)
    copy
    (copy-info-fn root-dir src dst cache)))

(defn make-fs
  [root-dir cache-dir]
  (let [cache-file  (io/file cache-dir cache-name)
        cache       {:read (u/read-edn cache-file) :write (atom {})}]
    (reify BlogFileSystem
      (copy [this src dst_dir]
            (let [copier (make-copier root-dir src dst_dir cache)]
              (reduce #(if (:outdated %2) (inc %1) %1) 0
                (map copier (list-files src)))))
      (info [_ file] (file-info root-dir file cache))
      (save [this]
            (.mkdirs cache-dir)
            (spit cache-file (with-out-str (pr @(:write cache))))
            (log/debug (count @(:write cache))
                       "entries were written to" (u/relative-path cache-dir cache-file))))))