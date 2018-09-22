(ns statique.builder2
  (:require
    [clojure.java.io :as io]
    [statique.util :as u]
    [statique.logging :as log]
    [statique.notes :as n]
    [statique.defaults :as defaults]
    [statique.fs :as fs]))

(def ^:private statique-version (u/get-version 'statique))
(def ^:private statique-string  (format "Statique %s" statique-version))
(def ^:private statique-link    (format "<a href=\"https://github.com/alexeypegov/statique\">%s</a>",
                                        statique-string))

(defn- transform-file
  [base-url file]
  (let [links-extension (renderers/media-extension base-url)]
    (assoc
      (markdown/transform (slurp file) :extensions links-extension)
      :file file)))

(defn- render-single-note
  [note]
  (log/debug "note to generate" (:src note)))

(defn- make-global-vars
  [vars]
  (assoc vars
    :statique       statique-string
    :statique-link  statique-link))

(defn- as-file
  [config key]
  (let [root-dir (:root config)
        value (get-in config [:general key])]
    (io/file root-dir value)))

(defn build-fs
  [config]
  (let [root-dir    (io/as-file (:root config))
        cache-dir   (as-file config :cache)
        notes-dir   (as-file config :notes)
        output-dir  (as-file config :output)]
    (fs/make-blog-fs
      (fs/make-dirs root-dir cache-dir notes-dir output-dir))))

(defn build
  [blog-config]
  (let [config (merge-with into defaults/config blog-config)]
    (with-open [fs          (build-fs config)]
      (let [global-vars (make-global-vars (:vars config))]
        (doall (map render-single-note (n/outdated-single-notes fs)))))))