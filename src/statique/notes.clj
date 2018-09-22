(ns statique.notes
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [statique.logging :as log]
            [statique.util :as u]))

(def ^:private out-ext      "html")
(def ^:private markdown-ext ".md")

(defrecord Page [index notes next?])
(defrecord Note [src dst])

(defn- slug
  [file]
  (s/lower-case (s/replace (.getName file) markdown-ext "")))

(defn- add-note-destination
  [output-dir info]
  (let [filename (format "%s.%s" (slug (:src info)) out-ext)]
    (assoc info
      :dst (io/file output-dir filename))))

(defn- add-destination-outdated-flag
  [info]
  (assoc info
    :destination-outdated (not (.exists (:dst info)))))

(defn- render-outdated-notes
  [info]
  (if (:outdated info)
    (do
      (log/debug (:relative info))
      info)))

(defn- add-source-outdated-flag
  [info]
  (let [{:keys [timestamp cached-timestamp]} info]
    (assoc info
      :source-outdated (not= timestamp cached-timestamp))))

(defn- make-note
  [info]
  (Note. (:src info) (:dst info)))

(defn- make-single-note-info
  [fs infos]
  (let [output-dir          (.output-dir fs)
        add-destination-fn  (partial add-note-destination output-dir)]
    (map (comp
           add-destination-outdated-flag
           add-destination-fn
           add-source-outdated-flag)
         infos)))

(defn outdated-single-notes
  [fs]
  (map make-note
    (filter
      #(if
         (or
           (:source-outdated %)
           (:destination-outdated %))
         %)
      (make-single-note-info fs (.notes fs)))))

(defn make-page
  [info]
  (->Page (:index info) (:items info) (:next? info)))

(defn- add-page-outdated-flag
  [info]
  (if-let [outdated (some #(= true (:outdated %)) (:items info))]
    (assoc info :outdated true)
    (if-let [cache-outdated true]
      (assoc info :outdated true)
      info)))

#_(defn outdated-page-notes
  [fs page-size]
  (map make-page
    (filter
      #(if (:outdated %) %)
      (map
        add-page-outdated-flag
        (u/paged-seq page-size (annotated-files (.notes fs)))))))