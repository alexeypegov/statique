(ns statique.notes
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [statique.logging :as log]
            [statique.util :as u]))

(def markdown-ext ".md")
(def out-ext      "html")

(def ^:dynamic *fs* nil)
(def ^:dynamic *out-dir* nil)
(def ^:dynamic *page-size* nil)

(defrecord Page [index notes next?])
(defrecord Note [src dst])

(defn- slug
  [file]
  (s/lower-case (s/replace (.getName file) markdown-ext "")))

(defn- note-files
  [dir]
  (reverse (u/sorted-files dir :postfix ".md")))

(defn- add-info
  [file]
  (.info *fs* file))

(defn- add-note-destination
  [info]
  (let [filename (format "%s.%s" (slug (:src info)) out-ext)]
    (assoc info
      :dst (io/file *out-dir* filename))))

(defn- add-existence-flag
  [info]
  (assoc info :dst-exists (.exists (:dst info))))

(defn- render-outdated-notes
  [info]
  (if (:outdated info)
    (do
      (log/debug (:relative info))
      info)))

(defn- add-outdated-flag
  [info]
  (let [{:keys [timestamp cached-timestamp dst-exists]} info]
    (if-let [outdated (or (not= timestamp cached-timestamp) (not dst-exists))]
      (assoc info :outdated true))))

(defn- make-note
  [info]
  (->Note (:src info) (:dst info)))

(defn- annotated-files
  [files]
  (map (comp
         add-outdated-flag
         add-existence-flag
         add-note-destination
         add-info)
       files))

(defn- outdated-single-notes
  [files]
  (map make-note
    (filter
      #(if (:outdated %) %)
      (annotated-files files))))

(defn- add-page-outdated-flag
  [info]
  (if-let [outdated (some #(= true (:outdated %)) (:items info))]
    (assoc info :outdated true)
    (if-let [cache-outdated true]
      (assoc info :outdated true)
      info)))

(defn make-page
  [info]
  (->Page (:index info) (:items info) (:next? info)))

(defn- outdated-page-notes
  [files]
  (map make-page
    (filter
      #(if (:outdated %) %)
      (map
        add-page-outdated-flag
        (u/paged-seq *page-size* (annotated-files files))))))

(defn outdated-items
  [fs notes-dir out-dir page-size]
  (binding [*fs*        fs
            *out-dir*   out-dir
            *page-size* page-size]
    (let [files (note-files notes-dir)
          notes (doall (outdated-single-notes files))
          pages (doall (outdated-page-notes files))]
      (concat notes pages))))