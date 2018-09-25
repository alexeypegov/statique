(ns statique.notes
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [statique.logging :as log]
            [statique.util :as u]))

(def ^:private out-ext      ".html")
(def ^:private markdown-ext ".md")

(defn- slug
  [file]
  (let [name (.getName file)]
    (s/lower-case (subs name 0 (- (count name) (count markdown-ext))))))

(defn- note-info
  [output-dir {:keys [src timestamp cached-timestamp] :as note}]
  (let [slug            (slug src)
        filename        (str slug out-ext)
        dst             (io/file output-dir filename)
        dst-outdated    (not (.exists dst))
        src-outdated    (not= timestamp cached-timestamp)]
    (assoc note
      :outdated (or src-outdated dst-outdated)
      :dst      dst
      :slug     slug)))

(defn outdated-single-notes
  [fs]
  (let [output-dir    (.output-dir fs)
        note-info-fn  (partial note-info output-dir)]
    (filter :outdated (map note-info-fn (.note-files fs)))))

(defn- paged-seq
  [fs page-size]
  (u/paged-seq
    page-size
    (map
      (fn [{:keys [timestamp cached-timestamp] :as info}]
        (assoc info :outdated (not= timestamp cached-timestamp)))
      (.note-files fs))))

(defn- page-info
  [fs {:keys [items] :as info}]
  (if-let [has-outdated-note (some :outdated items)]
    (assoc info :outdated true)
    (if-let [cache-outdated false] ; todo!!!
      info
      (assoc info :outdated true))))

(defn outdated-paged-notes
  [fs page-size]
  (let [output-dir    (.output-dir fs)
        page-info-fn  (page-info fs output-dir)]
    (filter
      :outdated
      (map page-info-fn (paged-seq page-size fs)))))