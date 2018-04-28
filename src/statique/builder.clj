(ns statique.builder
  (:require [slingshot.slingshot :only [throw+]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [statique.defaults :as defaults]
            [statique.markdown :as markdown]
            [statique.freemarker :as freemarker]
            [statique.renderers :as renderers]
            [statique.noembed :as noembed]
            [statique.logging :as log]
            [statique.util :as u]
            [me.raynes.fs :as fs]
            [clj-rss.core :as rss]))

(def encoding       "UTF-8")
(def out-ext        "html")
(def markdown-ext   ".md")

(defn- slug
  [file]
  (string/lower-case (string/replace (.getName file) markdown-ext "")))

(defn- as-file
  [config key]
  (let [root-dir  (:root config)
        general   (:general config)
        name      (get general key)]
    (io/file root-dir name)))

(defn- output-dir
  [config]
  (as-file config :output))

(defn- note-files
  [config]
  (let [dir (as-file config :notes)]
    (reverse (u/sorted-files dir))))

(defn- note-pages
  [page-size col & {:keys [ndx] :or {ndx 1}}]
  (lazy-seq
    (when (seq col)
      (let [items (take page-size col)
            rest  (drop page-size col)
            next? (not (empty? rest))]
          (cons {:items (doall items)
                 :ndx   ndx
                 :next  (if next? (inc ndx) -1)
                 :prev  (if (> ndx 1) (dec ndx) -1)}
                (note-pages page-size rest :ndx (inc ndx)))))))

(defn- transform-file
  [base-url noembed file]
  (let [links-extension (renderers/media-extension base-url noembed)]
    (assoc
      (markdown/transform (slurp file) :extensions links-extension)
      :file file)))

(defn- transform-note
  [base-url noembed file]
  (assoc
    (transform-file base-url noembed file)
    :slug (slug file)))

(defn- make-page-filename
  [ndx]
  (if (= ndx 1)
    (format "index.%s" out-ext)
    (format "page-%d.%s" ndx out-ext)))

(defn- freemarker-transformer
  [config]
  (let [theme-dir  (as-file config :theme)
        fmt-config (freemarker/make-config theme-dir)]
    (partial freemarker/render fmt-config)))

(defn- make-writer
  [config]
  (let [output (output-dir config)]
    (partial u/write-file output)))

(defn- render-notes
  [config notes]
  (let [notes-per-page  (get-in config [:general :notes-per-page])
        rss-notes       (get-in config [:general :rss :count])
        feeds           (get-in config [:general :rss :feeds])
        writer          (make-writer config)
        global-vars     (:vars config)
        to-html         (freemarker-transformer config)]
    (do
      ; write single notes
      (log/info (count (pmap (comp
                               writer
                               #(assoc %
                                  :content (to-html "note" %)
                                  :filename (format "%s.%s" (get-in % [:note :slug]) out-ext))
                               #(assoc {} :vars global-vars :note %))
                             notes))
                "notes were written")
      ; write paged notes
      (log/info (count (pmap (comp
                               writer
                               #(assoc %
                                  :content (to-html "index" %)
                                  :filename (make-page-filename (:ndx %)))
                               #(assoc % :vars global-vars))
                             (note-pages notes-per-page notes)))
                "pages were written")
      (when (> rss-notes 0)
        (pmap (comp
                writer
                #(assoc %
                   :content (to-html (get % :template) %)
                   :filename (format "%s.%s" (get % :template) "xml"))
                #(assoc {}
                   :vars global-vars
                   :items (take rss-notes notes)
                   :template %))
              feeds)))))

(defn- prepare-notes
  [config noembed]
  (let [base-url        (get-in config [:general :base-url])
        transform       (partial transform-note base-url noembed)
        date-format     (get-in config [:general :date-format])
        date-formatter  (u/local-formatter date-format)]
    (pmap #(assoc %
             :link        (format "%s%s.%s" base-url (:slug %) out-ext)
             :parsed-date (u/parse-date date-formatter (:date %)))
      (filter #(not (:draft %)) (pmap transform (note-files config))))))

(defn make-noembed
  [config]
  (noembed/make-noembed (as-file config :cache)))

(defn- render-standalone
  [config noembed]
  (let [pages-dir (as-file config :pages)]
    (if (.exists pages-dir)
      (let [pages       (u/sorted-files pages-dir)
            base-url    (get-in config [:general :base-url])
            to-html     (freemarker-transformer config)
            global-vars (:vars config)
            writer      (make-writer config)
            from-md     (partial transform-file base-url noembed)]
        (log/info (count (pmap (comp
                                 writer
                                 #(assoc {}
                                    :content (to-html "page" %)
                                    :filename (format "%s.%s"
                                                      (u/file-name (:file %))
                                                      out-ext))
                                 #(assoc %
                                    :vars global-vars)
                                 from-md)
                               pages))
                  "standalone pages were written")))))

(defn- render
  [config]
  (let [noembed (make-noembed config)]
    (do
      (doall (render-notes config (prepare-notes config noembed)))
      (render-standalone config noembed)
      (.save noembed))))

(defn- copy
  [config file]
  (let [root (:root config)
        out  (output-dir config)
        f    (io/file root file)]
    (if (.isDirectory f)
      (fs/copy-dir f out)
      (fs/copy f (io/file out (fs/base-name file))))))

(defn- copy-static
  [config]
  (when-let [ds (get-in config [:general :copy])]
    (let [cp (partial copy config)]
      (log/info (count (pmap cp ds)) "files/dirs were copied"))))

(defn- clean
  [config]
  (let [out (output-dir config)]
    (fs/delete-dir out)))

(defn build
  [blog-config]
  (let [config (merge-with into defaults/config blog-config)]
    (do
      (clean config)
      (render config)
      (copy-static config))))