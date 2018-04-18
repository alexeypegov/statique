(ns statique.builder
  (:require [slingshot.slingshot :only [throw+]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [statique.markdown :as markdown]
            [statique.freemarker :as freemarker]
            [statique.renderers :as renderers]
            [statique.noembed :as noembed]
            [statique.logging :as log]
            [me.raynes.fs :as fs]))

(def encoding       "UTF-8")
(def out-ext        "html")
(def markdown-ext   ".md")
(def default-output "./out/")

(defn- ext-filter
  [ext]
  (reify java.io.FilenameFilter
    (accept [this dir name]
            (string/ends-with? name ext))))

(defn- slug
  [file]
  (string/lower-case (string/replace (.getName file) markdown-ext "")))

(defn- file-comparator
  [file1 file2]
  (compare (.getName file1) (.getName file2)))

(defn- as-file
  [config key default]
  (let [root-dir  (:root config)
        general   (:general config {})
        name      (get general key default)]
    (io/file root-dir name)))

(defn- note-files
  [config]
  (let [dir (as-file config :notes "notes/")]
    (reverse (sort file-comparator (.listFiles dir (ext-filter markdown-ext))))))

(defn- pages
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
                (pages page-size rest :ndx (inc ndx)))))))

(defn- file-writer
  [output-dir {:keys [content filename]}]
  {:pre [(string? content) (string? filename)]}
  (let [file (io/file output-dir filename)]
    (do
      (.mkdirs (.getParentFile file))
      (with-open [w (io/writer file)]
        (.write w content)
        (.getPath file)))))

(defn- transform-note
  [noembed file]
  (let [links-extension (renderers/media-extension noembed)]
    (assoc
      (markdown/transform (slurp file) :extensions links-extension)
      :file file)))

(defn- make-page-filename
  [ndx]
  (if (= ndx 1)
    (format "index.%s" out-ext)
    (format "page-%d.%s" ndx out-ext)))

(defn- freemarker-transformer
  [config]
  (let [theme-dir  (as-file config :theme "theme/")
        fmt-config (freemarker/make-config theme-dir)]
    (partial freemarker/render fmt-config)))

(defn- make-writer
  [config]
  (let [output-dir (as-file config :output default-output)]
    (partial file-writer output-dir)))

(defn- render-notes
  [config notes]
  (let [notes-per-page  (get-in config [:general :notes-per-page] 5)
        writer          (make-writer config)
        global-vars     (:vars config)
        to-html         (freemarker-transformer config)]
    (do
      ; todo: extract
      ; write single notes
      (log/info (count (pmap (comp
                               writer
                               #(assoc %
                                  :content (to-html "note" %)
                                  :filename (format "%s.%s" (slug (get-in % [:note :file])) out-ext))
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
                             (pages notes-per-page notes)))
                "pages were written"))))

(defn- prepare-notes
  [config noembed]
  (let [transform (partial transform-note noembed)]
    (pmap transform (note-files config))))

(defn make-noembed
  [config]
  (noembed/make-noembed (as-file config :cache "cache/")))

(defn- render-standalone
  [config]
  (when-let [pages (get-in config [:general :pages])]
    (let [to-html     (freemarker-transformer config)
          global-vars (:vars config)
          writer      (make-writer config)]
      (log/info (count (pmap (comp
                               writer
                               #(assoc {}
                                  :content (to-html % {:vars global-vars})
                                  :filename (format "%s.%s" % out-ext)))
                             pages))
                "standalone pages were written"))))

(defn- render
  [config]
  (let [noembed (make-noembed config)]
    (do
      (doall (render-notes config (prepare-notes config noembed)))
      (.save noembed)
      (render-standalone config))))

(defn- copy-static
  [config]
  (when-let [ds (get-in config [:general :copy])]
    (let [root   (config :root)
          output (as-file config :output default-output)]
      (log/info (count (pmap
                         #(fs/copy-dir (io/file root %) output)
                       ds))
                "dirs were copied"))))

(defn build
  [config]
  (do
    (render config)
    (copy-static config)))