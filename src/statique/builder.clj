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
            [statique.fs :as fs2]
            [me.raynes.fs :as fs]))

(def encoding         "UTF-8")
(def out-ext          "html")
(def markdown-ext     ".md")
(def statique-version (u/get-version 'statique))
(def statique-string  (format "Statique %s" statique-version))
(def statique-link    (format "<a href=\"https://github.com/alexeypegov/statique\">%s</a>",
                              statique-string))

(def ^:dynamic *noembed* nil)
(def ^:dynamic *config* nil)
(def ^:dynamic *fs* nil)

(defn- slug
  [file]
  (string/lower-case (string/replace (.getName file) markdown-ext "")))

(defn- as-file
  [key]
  (let [root-dir  (:root *config*)
        general   (:general *config*)
        name      (get general key)]
    (io/file root-dir name)))

(defn- output-dir
  []
  (as-file :output))

(defn- note-files
  []
  (let [dir (as-file :notes)]
    (reverse (u/sorted-files dir :postfix ".md"))))

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
  [base-url file]
  (let [links-extension (renderers/media-extension *noembed* base-url)]
    (assoc
      (markdown/transform (slurp file) :extensions links-extension)
      :file file)))

(defn- transform-note
  [base-url file]
  (assoc
    (transform-file base-url file)
    :slug (slug file)))

(defn- make-page-filename
  [ndx]
  (if (= ndx 1)
    (format "index.%s" out-ext)
    (format "page-%d.%s" ndx out-ext)))

(defn- freemarker-transformer
  []
  (let [theme-dir  (as-file :theme)
        fmt-config (freemarker/make-config theme-dir)]
    (partial freemarker/render fmt-config)))

(defn- make-writer
  []
  (partial u/write-file (output-dir)))

(defn- prepare-notes
  [files & {:keys [base-url] :or {base-url nil}}]
  (let [transform       (partial transform-note base-url)
        date-format     (get-in *config* [:general :date-format])
        date-formatter  (u/local-formatter date-format)]
    (pmap #(assoc %
             :link        (format "%s%s.%s" base-url (:slug %) out-ext)
             :parsed-date (u/parse-date date-formatter (:date %)))
      (filter #(not (:draft %)) (pmap transform files)))))

(defn- render-everything
  []
  (let [pages-dir       (as-file :pages)
        writer          (make-writer)
        global-vars     (assoc (:vars *config*)
                          :statique statique-string
                          :statique-link statique-link)
        to-html         (freemarker-transformer)]
    (do
      (let [notes           (prepare-notes (note-files))
            notes-per-page  (get-in *config* [:general :notes-per-page])
            recent-notes    (take 10 notes)]
        ; write single notes
        (log/info (count (pmap (comp
                                 writer
                                 #(assoc %
                                    :content (to-html "note" %)
                                    :filename (format "%s.%s" (get-in % [:note :slug]) out-ext))
                                 #(assoc {}
                                    :vars global-vars
                                    :note %
                                    :recent recent-notes))
                               notes))
                  "notes were written")
        ; write paged notes
        (log/info (count (pmap (comp
                                 writer
                                 #(assoc %
                                    :content (to-html "index" %)
                                    :filename (make-page-filename (:ndx %)))
                                 #(assoc %
                                    :vars global-vars
                                    :recent recent-notes))
                               (note-pages notes-per-page notes)))
                  "pages were written"))
      ; write rss feeds
      (let [rss-count (get-in *config* [:general :rss :count])]
        (when (> rss-count 0)
          (let [base-url  (get-in *config* [:general :base-url])
                notes     (take rss-count (prepare-notes (note-files) :base-url base-url))
                feeds     (get-in *config* [:general :rss :feeds])]
            (pmap (comp
                    writer
                    #(assoc %
                       :content (to-html (get % :template) %)
                       :filename (format "%s.%s" (get % :template) "xml"))
                    #(assoc {}
                       :vars global-vars
                       :items notes
                       :template %))
                  feeds)
            (log/info (count feeds) "RSS feeds were written"))))
      ; write standalone pages
      (when (.exists pages-dir)
        (let [pages     (u/sorted-files pages-dir :postfix ".md")
              base-url  (get-in *config* [:general :base-url])
              from-md   (partial transform-file base-url)]
          (log/info (count (pmap (comp
                                   writer
                                   #(assoc {}
                                      :content (to-html "page" %)
                                      :filename (format "%s.%s"
                                                        (fs/name (:file %))
                                                        out-ext))
                                   #(assoc %
                                      :vars global-vars)
                                   from-md)
                                 pages))
                    "standalone pages were written"))))))

(defn make-noembed
  []
  (noembed/make-noembed (as-file :cache)))

(defn- render
  []
  (binding [*noembed* (make-noembed)]
    (do
      (doall
        (render-everything))
      (.save *noembed*))))

(defn- copy
  [file]
  (let [root (:root *config*)
        dst  (output-dir)
        src  (io/file root file)]
    (.copy *fs* src dst)))

(defn- copy-static
  []
  (when-let [ds (get-in *config* [:general :copy])]
    (log/info (reduce + 0 (pmap copy ds)) "static files were copied")))

(defn- clean
  []
  (fs/delete-dir (output-dir)))

(defn build-fs
  [config]
  (let [root (:root config)
        cache-dir (io/file root (get-in config [:general :cache]))]
    (fs2/make-fs root cache-dir)))

(defn build
  [blog-config]
  (let [config (merge-with into defaults/config blog-config)]
    (binding [*config*  config
              *fs*      (build-fs config)]
      (do
        (clean)
        (render)
        (copy-static)
        (.save *fs*)))))