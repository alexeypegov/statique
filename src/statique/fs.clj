(ns statique.fs
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.logging :as log]
            [me.raynes.fs :as fs]))

(def ^:private cache-name "timestamp.edn")

(defprotocol BlogFileSystem
  (copy [this src dst-dir])
  (save [this]))

(defn- list-files
  [file-or-dir]
  (let [file (io/file file-or-dir)]
    (if (.isDirectory file)
      (filter #(not (.isDirectory %)) (file-seq file))
      (list file))))

(defn- info
  [src dst cache]
  (fn [file]
    (let [ts        (.lastModified file)
          relative  (u/relative-path (.getParent src) file)
          cached-ts (get (:read cache) relative 0)
          dst       (io/file dst relative)
          exists    (.exists dst)]
      (assoc {}
        :relative relative
        :timestamp ts
        :cached-timstamp cached-ts
        :exists exists
        :outdated (or (not exists) (not= ts cached-ts))
        :src file
        :dst dst))))

(def copy
  (fn [m]
    (if (:outdated m)
      (fs/copy+ (:src m) (:dst m)))
    m))

(defn- update-cache
  [cache]
  (fn [m]
    (swap! (:write cache) assoc (:relative m) (:timestamp m))
    m))

(defn- make-copier
  [src dst cache]
  (comp
    (update-cache cache)
    copy
    (info src dst cache)))

(defn make-fs
  [root-dir cache-dir]
  (let [cache-file  (io/file cache-dir cache-name)
        cache       {:read (u/read-edn cache-file) :write (atom {})}]
    (reify BlogFileSystem
      (copy [this src dst_dir]
            (let [copier (make-copier src dst_dir cache)]
              (reduce #(if (:outdated %2) (inc %1) %1) 0
                (map copier (list-files src)))))
      (save [this]
            (.mkdirs cache-dir)
            (spit cache-file (with-out-str (pr @(:write cache))))
            (log/debug (count @(:write cache))
                       "entries were written to" (u/relative-path cache-dir cache-file))))))