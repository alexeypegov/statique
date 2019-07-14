(ns statique.builder
  (:require
    [clojure.java.io :as io]
    [clj-uuid :as uuid]
    [statique.util :as util]
    [statique.logging :as log]
    [statique.notes :as notes]
    [statique.defaults :as defaults]
    [statique.fs :as fs]
    [statique.renderers :as renderers]
    [statique.markdown :as md]
    [statique.freemarker :as fm]))

(def ^:private output-ext           ".html")
(def ^:private rss-ext              ".xml")
(def ^:private freemarker-ext       ".ftl")
(def ^:private note-template        "note")
(def ^:private page-template        "index")
(def ^:private standalone-template  "page")

(def ^:private statique-string  (format "Statique %s" (util/get-version 'statique)))
(def ^:private statique-link    (format "<a href=\"https://github.com/alexeypegov/statique\">%s</a>",
                                        statique-string))

(def ^:private ^:dynamic *context* nil)

(defn- prepare-note-item
  [{:keys [src slug relative]} {:keys [extension extension-abs date-format tz note-cache]} use-cache]
  (if-let [cached-note (when use-cache (.get note-cache relative))]
    cached-note
    (let [note-text       (slurp src)
          transformed     (md/transform note-text extension)
          transformed-abs (md/transform note-text extension-abs)
          parsed-date     (util/parse-local-date date-format tz (:date transformed))
          rfc-822         (util/rfc-822 parsed-date)
          rfc-3339        (util/rfc-3339 parsed-date)
          note-link       (str "/" slug output-ext)
          uuid            (uuid/v3 uuid/+namespace-url+ note-link)
          note            (assoc transformed
                            :body-abs     (:body transformed-abs)
                            :slug         slug
                            :link         note-link
                            :rfc-822      rfc-822
                            :rfc-3339     rfc-3339
                            :uuid         uuid)]
      (.put note-cache relative note)
      note)))

(defn- handle-render-error
  [src {:keys [result error]}]
  (if result
    result
    (do
     (log/error "Error rendering" (.getPath src))
     error)))

(defn- render-single-note
  [{:keys [dst src] :as item}]
  (let [{:keys [fmt-config global-vars]}    *context*
        {title :title :as transformed-item} (prepare-note-item item *context* false)]
    (log/debug title "->" dst)
    (->> (assoc {} :note transformed-item :vars global-vars)
         (fm/render fmt-config note-template)
         (handle-render-error src)
         (util/write-file dst))))

(defn- render-page
  [{index :index dst :dst items :items next? :next?}]
  (let [{:keys  [fmt-config global-vars]} *context*
        transformed-items (map #(prepare-note-item % *context* true) items)]
    (log/debug "page" index "->" dst)
    (->> {:vars       global-vars
          :items      transformed-items
          :ndx        index
          :next-page  (when next? (notes/page-filename (inc index)))
          :prev-page  (when (> index 1) (notes/page-filename (dec index)))}
         (fm/render fmt-config page-template)
         (handle-render-error dst)
         (util/write-file dst))))

(defn- transform-feed-templates
  [names fs]
  (let [output-dir  (.output-dir fs)
        theme-dir   (.theme-dir fs)]
    (map (fn [name]
           (let [src                             (io/file theme-dir (str name freemarker-ext))
                 dst                             (io/file output-dir (str name rss-ext))
                 {:keys [crc-mismatch] :as info} (.info fs src)]
             (assoc info
               :name     name
               :dst      dst
               :outdated (or crc-mismatch (not (.exists dst))))))
         names)))

(defn- render-feeds
  [{rss-count :items templates :names} fs]
  (when (seq templates)
    (let [items         (take rss-count (notes/all-notes fs))
          has-outdated  (seq (filter :outdated items))
          ts            (transform-feed-templates templates fs)
          ts-outdated   (seq (filter :outdated ts))]
      (when (or has-outdated ts-outdated)
        (let [transformed (map #(prepare-note-item % *context* true) items)
              {:keys [base-url fmt-config global-vars]} *context*]
          (doseq [{:keys [name dst]} ts]
            (log/info "Render" name "feed")
            (log/debug "feed" name "->" dst)
            (->> {:vars     global-vars
                  :items    transformed
                  :base-url base-url
                  :name     name}
                 (fm/render fmt-config name)
                 (handle-render-error dst)
                 (util/write-file dst))))))))

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
        output-dir  (as-file config :output)
        pages-dir   (as-file config :pages)
        theme-dir   (as-file config :theme)]
    (fs/make-blog-fs
      (fs/make-dirs root-dir cache-dir notes-dir pages-dir theme-dir output-dir))))

(defn- render-standalone-page
  [{:keys [src dst]}]
  (let [{:keys [extension fmt-config global-vars]}  *context*
        page-text                                   (slurp src)
        {:keys [title] :as transformed}            (md/transform page-text extension)]
    (log/debug title "->" dst)
    (->> (assoc transformed :vars global-vars)
         (fm/render fmt-config standalone-template)
         (handle-render-error dst)
         (util/write-file dst))))

(defn- copy-static
  [fs dirs]
  (log/info
    (reduce + 0 (map #(.copy-to-output fs %) dirs))
    "static files were copied"))

(defn build
  [{vars :vars, {:keys [base-url date-format notes-per-page tz pages copy], :as general} :general, :as blog-config}]
  (let [config (defaults/with-defaults blog-config)]
    (with-open [fs (build-fs config)]
      (binding [*context*   {:base-url        base-url
                             :global-vars     (make-global-vars vars)
                             :fmt-config      (fm/make-config (as-file config :theme))
                             :date-format     date-format
                             :tz              tz
                             :extension       (renderers/media-extension)
                             :extension-abs   (renderers/media-extension base-url)
                             :note-cache      (.get-instant-cache fs "notes")}]
        (log/info "Rendering notes...")
        (log/info
          (count (map render-single-note (notes/outdated-notes fs)))
          "note(s) were rendered")
        (log/info "Rendering pages...")
        (log/info
          (count (map render-page (notes/outdated-pages fs notes-per-page)))
          "page(s) were rendered")
        (log/info "Render feeds...")
        (render-feeds (:feeds general) fs)
        (log/info "Render standalone pages...")
        (log/info
          (count (map render-standalone-page (notes/outdated-standalone-pages fs)))
          "standalone page(s) were rendered")
        (log/info "Copy static files...")
        (copy-static fs copy)))))