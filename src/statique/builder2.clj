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

(def ^:private output-ext       ".html")

(def ^:private statique-version (util/get-version 'statique))
(def ^:private statique-string  (format "Statique %s" statique-version))
(def ^:private statique-link    (format "<a href=\"https://github.com/alexeypegov/statique\">%s</a>",
                                        statique-string))

(def ^:private ^:dynamic *context* nil)

(defrecord Context [global-vars fmt-config media-extension date-formatter base-url])

(defn- render-single-note
  [{:keys [src dst slug]}]
  (let [note-text (slurp src)
        {:keys [media-extension date-formatter base-url fmt-config global-vars]} *context*
        {:keys [draft title date], :as note} (markdown/transform note-text media-extension)]
    (when-not draft
      (log/debug title "->" dst)
      (let [parsed-date (util/parse-date date-formatter date)
            note-link   (str (or base-url "/") slug output-ext)]
        (util/write-file-2
          dst
          (freemarker/render fmt-config "note" {:note        (assoc note :slug slug)
                                                :vars        global-vars
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
  [{:keys [root general]} key]
  (io/file root (key general)))

(defn build-fs
  [config]
  (let [root-dir    (io/as-file (:root config))
        cache-dir   (as-file config :cache)
        notes-dir   (as-file config :notes)
        output-dir  (as-file config :output)]
    (fs/make-blog-fs
      (fs/make-dirs root-dir cache-dir notes-dir output-dir))))

(defn build
  [{vars :vars, {:keys [base-url date-format notes-per-page]} :general, :as blog-config}]
  (let [config (defaults/with-defaults blog-config)]
    (with-open [fs (build-fs config)]
      (let [global-vars     (make-global-vars vars)
            fmt-config      (freemarker/make-config (as-file config :theme))
            date-formatter  (util/local-formatter date-format)
            note-extension  (renderers/media-extension)]
        (binding [*context* (->Context global-vars fmt-config note-extension date-formatter nil)]
          (doseq [note (notes/outdated-single-notes fs)]
            (render-single-note note))
          #_(doseq [page (notes/outdated-pages fs notes-per-page)]
            (render-page page)))))))