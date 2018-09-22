(ns statique.builder2
  (:require
    [clojure.java.io :as io]
    [statique.util :as u]
    [statique.logging :as log]
    [statique.notes :as n]
    [statique.defaults :as defaults]
    [statique.fs :as fs]
    [statique.renderers :as r]
    [statique.markdown :as md]
    [statique.freemarker :as fm]))

(def ^:private statique-version (u/get-version 'statique))
(def ^:private statique-string  (format "Statique %s" statique-version))
(def ^:private statique-link    (format "<a href=\"https://github.com/alexeypegov/statique\">%s</a>",
                                        statique-string))

(def ^:private ^:dynamic *context* nil)

(defrecord Context [global-vars fmt-config media-extension date-formatter])

#_(defn- freemarker-transformer
  []
  (let [theme-dir  (as-file :theme)
        fmt-config (fm/make-config theme-dir)]
    (partial f/render fmt-config)))

#_(defn- prepare-notes
  [files & {:keys [base-url] :or {base-url nil}}]
  (let [transform       (partial transform-note base-url)
        date-format     (get-in *config* [:general :date-format])
        date-formatter  (u/local-formatter date-format)]
    (pmap #(assoc %
             :link        (format "%s%s.%s" base-url (:slug %) out-ext)
             :parsed-date (u/parse-date date-formatter (:date %)))
      (filter #(not (:draft %)) (pmap transform files)))))

(defn- render-single-note
  [info]
  (let [note-text (slurp (:src info))
        extension (:media-extension *context*)
        note      (md/transform note-text extension)]
    (when-not (:draft note)
      (log/debug (:title note)))))

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
    (with-open [fs (build-fs config)]
      (let [global-vars     (make-global-vars (:vars config))
            fmt-config      (fm/make-config (as-file config :theme))
            date-format     (get-in config [:general :date-format])
            date-formatter  (u/local-formatter date-format)
            note-extension  (r/media-extension)]
        (binding [*context* (Context. global-vars fmt-config note-extension date-formatter)]
          (doall (map render-single-note (n/outdated-single-notes fs))))))))