(ns statique.builder2
  (:require
    [clojure.java.io :as io]
    [statique.util :as util]
    [statique.logging :as log]
    [statique.notes :as notes]
    [statique.defaults :as defaults]
    [statique.fs :as fs]
    [statique.renderers :as renderers]
    [statique.markdown :as markdown]
    [statique.freemarker :as freemarker]))

(def ^:private statique-version (util/get-version 'statique))
(def ^:private statique-string  (format "Statique %s" statique-version))
(def ^:private statique-link    (format "<a href=\"https://github.com/alexeypegov/statique\">%s</a>",
                                        statique-string))
(def ^:private output-ext       ".html")

(def ^:private ^:dynamic *context* nil)

(defrecord Context [global-vars fmt-config media-extension date-formatter base-url])

(defn- render-single-note
  [{:keys [src dst slug]}]
  (let [note-text                           (slurp src)
        extension                           (:media-extension *context*)
        {:keys [draft title date] :as note} (markdown/transform note-text extension)]
    (when-not draft
      (log/debug title "->" dst)
      (let [parsed-date (util/parse-date (:date-formatter *context*) date)
            base-url    (:base-url *context*)
            note-link   (str (or base-url "/") slug output-ext)
            fmt-config  (:fmt-config *context*)]
        (util/write-file-2
          dst
          (freemarker/render fmt-config "note" {:note        (assoc note :slug slug)
                                                :vars        (:global-vars *context*)
                                                :link        note-link
                                                :parsed-date parsed-date}))))))

(defn- render-page
  [info]
  (log/debug "page"))

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
  (let [config (defaults/with-defaults blog-config)]
    (with-open [fs (build-fs config)]
      (let [global-vars     (make-global-vars (:vars config))
            fmt-config      (freemarker/make-config (as-file config :theme))
            base-url        (get-in config [:general :base-url])
            date-format     (get-in config [:general :date-format])
            date-formatter  (util/local-formatter date-format)
            note-extension  (renderers/media-extension)
            page-size       (get-in config [:general :notes-per-page])]
        (binding [*context* (->Context global-vars fmt-config note-extension date-formatter nil)]
          (doseq [note (notes/outdated-single-notes fs)]
            (render-single-note note))
          #_(doseq [page (notes/outdated-pages fs page-size)]
            (render-page page)))))))