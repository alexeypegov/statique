(ns statique.notes
  (:require [clojure.java.io :as io]
            [statique.logging :as log]
            [statique.util :as util]))

(def ^:private out-ext      ".html")
(def ^:private markdown-ext ".md")

(defn- note-info
  [output-dir {:keys [src slug crc-mismatch], :as note}]
  (let [filename        (str slug out-ext)
        dst             (io/file output-dir filename)
        dst-outdated    (not (.exists dst))]
    (assoc note
      :outdated     (or crc-mismatch dst-outdated)
      :dst          dst)))

(defn all-notes
  [fs]
  (let [output-dir    (.output-dir fs)
        note-info-fn  (partial note-info output-dir)]
    (map note-info-fn (.note-files fs))))

(defn outdated-notes
  [fs]
  (filter :outdated (all-notes fs)))

(defn- paged-seq
  [fs page-size]
  (util/paged-seq page-size (.note-files fs)))

(defn- page-filename
  [ndx]
  (let [prefix (if (= 1 ndx) "index" (str "page-" ndx))]
    (str prefix out-ext)))

(defn- page-info
  [page-cache output-dir {items :items, index :index, :as page}]
  (let [has-outdated-notes  (some :crc-mismatch items)
        dst                 (io/file output-dir (page-filename index))
        dst-outdated        (not (.exists dst))
        cached-items        (.get page-cache index)
        page-items          (map :relative items)
        cached-outdated     (not= cached-items page-items)]
    (.put page-cache index page-items)
    (assoc page
      :dst dst
      :outdated (or has-outdated-notes dst-outdated cached-outdated))))

(defn outdated-pages
  [fs page-size]
  (let [output-dir    (.output-dir fs)
        page-cache    (.cache fs "pages")
        page-info-fn  (partial page-info page-cache output-dir)]
    (filter
      :outdated
      (map page-info-fn (paged-seq fs page-size)))))

(defn- standalone-info
  [output-dir {:keys [src crc-mismatch slug], :as file}]
  (let [filename        (str slug out-ext)
        dst             (io/file output-dir filename)
        dst-outdated    (not (.exists dst))]
    (assoc file
      :outdated (or crc-mismatch dst-outdated)
      :dst      dst)))

(defn- standalone-pages
  [fs]
  (let [standalone-info-fn  (partial standalone-info (.output-dir fs))]
    (map standalone-info-fn (.page-files fs))))

(defn outdated-standalone-pages
  [fs]
  (filter :outdated (standalone-pages fs)))