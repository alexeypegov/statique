(ns statique.notes
  (:require [clojure.java.io :as io]
            [statique.logging :as log]
            [statique.util :as util]))

(def ^:private out-ext      ".html")
(def ^:private markdown-ext ".md")

(defn- note-info
  [output-dir {:keys [src slug crc cached-crc], :as note}]
  (let [filename        (str slug out-ext)
        dst             (io/file output-dir filename)
        dst-outdated    (not (.exists dst))
        src-outdated    (not= crc cached-crc)]
    (assoc note
      :src-outdated src-outdated
      :outdated     (or src-outdated dst-outdated)
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
  (util/paged-seq
    page-size
    (map
      (fn [{:keys [crc cached-crc], :as info}]
        (assoc info :outdated (not= crc cached-crc)))
      (.note-files fs))))

(defn- page-filename
  [ndx]
  (let [prefix (if (= 1 ndx) "index" (str "page-" ndx))]
    (str prefix out-ext)))

(defn- page-info
  [page-cache output-dir {items :items, index :index, :as page}]
  (let [has-outdated-notes  (some :outdated items)
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
  [output-dir {:keys [src crc cached-crc slug], :as file}]
  (let [filename        (str slug out-ext)
        dst             (io/file output-dir filename)
        dst-outdated    (not (.exists dst))
        src-outdated    (not= crc cached-crc)]
    (assoc file
      :outdated (or src-outdated dst-outdated)
      :dst      dst)))

(defn- standalone-pages
  [fs]
  (let [standalone-info-fn  (partial standalone-info (.output-dir fs))]
    (map standalone-info-fn (.page-files fs))))

(defn outdated-standalone-pages
  [fs]
  (filter :outdated (standalone-pages fs)))