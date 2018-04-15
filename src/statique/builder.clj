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

(def encoding     "UTF-8")
(def out-ext      "html")
(def markdown-ext ".md")

(defn- ext-filter
  [ext]
  (reify java.io.FilenameFilter
    (accept [this dir name]
            (string/ends-with? name ext))))

(defn- value-or-default
  [config key-name & {:keys [default] :or {default nil}}]
  (get config (keyword key-name) default))

(defn- slug
  [file]
  (string/lower-case (string/replace (.getName file) markdown-ext "")))

(defn- file-comparator
  [file1 file2]
  (compare (.getName file1) (.getName file2)))

(defn- list-notes
  [dir]
  (reverse (sort file-comparator (.listFiles dir (ext-filter markdown-ext)))))

(defn- pages
  [page-size col & {:keys [ndx] :or {ndx 1}}]
  (lazy-seq
    (when (seq col)
      (let [page-items (take page-size col)
            rest-items (drop page-size col)]
          (cons {:items page-items
                 :rest rest-items
                 :index ndx}
                (pages page-size rest-items :ndx (inc ndx)))))))

(defn- write-to-file
  [file content & {:keys [create-dirs] :or {create-dirs true}}]
  (when (true? create-dirs)
    (.mkdirs (.getParentFile file)))
  (with-open [w (io/writer file)]
    (.write w content)))

(defn- single-note-writer
  [output-dir {:keys [content file]}]
  (let [output-file (io/file output-dir (str (slug file) out-ext))]
    (write-to-file output-file content)))

(defn- fmt->html
  [fmt-config template-name vars note]
  (let [render (partial freemarker/render fmt-config template-name)]
    (if (seq? note)
      {:content (render (assoc vars :notes note))}
      (assoc note :content (render (assoc vars :note note))))))

(defn- generate-notes
  [fmt-config vars output-dir notes]
  (let [to-html (partial fmt->html fmt-config "note" vars)
        to-file (partial single-note-writer output-dir)]
    (map (comp to-file to-html) notes)))

(defn- transform-note
  [noembed file]
  (let [links-extension (renderers/media-extension noembed)]
    (assoc
      (markdown/transform (slurp file) :extensions links-extension)
      :file file)))

(defn- prepare-page-file
  [output-dir ndx]
  (io/file output-dir
    (cond
      (= ndx 1)   (format "index.%s" out-ext)
      :else       (format "page-%d.%s" ndx out-ext))))

(defn- generate-page
  [fmt-config vars output-dir ndx notes next?]
  (let [prev       (if (> ndx 1) (dec ndx) -1)
        next       (if next? (inc ndx) -1)
        to-html    (partial fmt->html fmt-config "index" (assoc vars :index ndx :prev prev :next next))
        file       (prepare-page-file output-dir ndx)]
    (write-to-file file (:content (to-html notes)))
    #_(generate-notes fmt-config vars output-dir notes)))

(defn- page-processor
  [fmt-config vars output-dir noembed page]
  (let [page-notes  (:items page)
        page-index  (:index page)
        rest-notes  (:rest page)
        transformer (partial transform-note noembed)
        notes       (pmap transformer page-notes)
        next?       (not (empty? rest-notes))]
    (generate-page fmt-config vars output-dir page-index notes next?)))

(defn process-dir
  [notes-dir output-dir theme-dir fmt-config notes-per-page noembed vars]
  (let [notes       (list-notes notes-dir)
        processor   (partial page-processor fmt-config vars output-dir noembed)]
    (log/info (count notes) "notes were processed...")
    (let [pages (count (pmap processor (pages notes-per-page notes)))]
      (log/info pages "pages were generated..."))))

(defn- copy
  [general root output]
  (log/debug "Coping dirs...")
  (when-let [ds (general :copy)]
    (log/debug "copied" ds)
    (mapv #(fs/copy-dir (io/file root %) output) ds)))

(defn- generate-notes
  [general root-dir theme-dir output fmt-config noembed vars]
  (let [notes-dir       (io/file root-dir (general :notes :default "notes/"))
        notes-per-page  (general :notes-per-page :default 5)]
    (time (process-dir notes-dir output theme-dir fmt-config notes-per-page noembed vars))))

(defn- render-page
  [fmt-config vars name]
  {:content (freemarker/render fmt-config name vars)
   :name name})

(defn- page-writer
  [dir {:keys [content name]}]
  (let [file (io/file dir (format "%s.%s" name out-ext))]
    (write-to-file file content)
    name))

(defn- render-pages
  [general root theme output fmt-config noembed vars]
  (log/debug "Rendering standalone pages...")
  (when-let [pages (general :pages)]
    (let [renderer    (partial render-page fmt-config vars)
          writer      (partial page-writer output)]
      (doall (map (comp log/info writer renderer) pages)))))

(defn build
  [root-dir config]
  (let [general       (partial value-or-default (:general config {}))
        output-dir    (io/file root-dir (general :output :default "./out/"))
        theme-dir     (io/file root-dir (general :theme :default "theme/"))
        cache-dir     (io/file root-dir (general :cache :default "cache/"))
        fmt-config    (freemarker/make-config theme-dir)
        noembed       (noembed/make-noembed cache-dir)
        global-vars   (:vars config {})]
    (do
      (generate-notes general root-dir theme-dir output-dir fmt-config noembed global-vars)
      (.save noembed)
      (render-pages general root-dir theme-dir output-dir fmt-config noembed global-vars)
      (copy general root-dir output-dir))))