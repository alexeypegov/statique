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
(def ^:private note-template    "note")
(def ^:private page-template    "index")

(def ^:private statique-version (util/get-version 'statique))
(def ^:private statique-string  (format "Statique %s" statique-version))
(def ^:private statique-link    (format "<a href=\"https://github.com/alexeypegov/statique\">%s</a>",
                                        statique-string))

(def ^:private ^:dynamic *context* nil)

(defn- prepare-note-item
  [{:keys [src slug relative], :as item} {:keys [media-extension date-formatter base-url note-cache]}]
  (if-let [cached-note (get @note-cache relative)]
    cached-note
    (let [note-text   (slurp src)
          transformed (markdown/transform note-text media-extension)
          parsed-date (util/parse-date date-formatter (:date transformed))
                      ; todo: do we really need base-url here?
          note-link   (str (or base-url "/") slug output-ext)
          note        (assoc transformed
                        :slug         slug
                        :link         note-link
                        :parsed-date  parsed-date)]
      (swap! note-cache assoc relative note)
      note)))

(defn- render-single-note
  [{:keys [src dst slug relative], :as item}]
  (let [{:keys [fmt-config global-vars]}      *context*
        {title :title, :as transformed-item}  (prepare-note-item item *context*)]
    (log/debug title "->" dst)
    (util/write-file-2
      dst
      (freemarker/render fmt-config note-template (assoc transformed-item :vars global-vars)))))

(defn- render-page
  [{index :index, dst :dst, items :items, next? :next?}]
  (let [{:keys  [fmt-config global-vars media-extension note-cache]} *context*
        transformed-items (map #(prepare-note-item % *context*) items)]
    (log/debug "page" index "->" dst)
    (util/write-file-2
      dst
      (freemarker/render fmt-config page-template {:vars  global-vars
                                                   :items transformed-items
                                                   :ndx   index
                                                   :next  (if next? (inc index) -1)
                                                   :prev  (if (> index 1) (dec index) -1)}))))

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
      (binding [*context*   {:global-vars     (make-global-vars vars)
                             :fmt-config      (freemarker/make-config (as-file config :theme))
                             :date-formatter  (util/local-formatter date-format)
                             :media-extension (renderers/media-extension)
                             :note-cache      (atom {})}]
        #_(doseq [note (notes/outdated-notes fs)]
          (render-single-note note))
        (doseq [page (notes/outdated-pages fs notes-per-page)]
          (render-page page))))))