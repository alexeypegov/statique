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

(def file-ext           ".md")
(def def-notes          "notes/")
(def def-theme          "theme/")
(def def-output         "./out/")
(def def-output-ext     "html")
(def def-encoding       "UTF-8")
(def def-static         "static/")
(def def-cache          "cache/")
(def noembed-cache-name "noembed.edn")
(def def-notes-per-page 5)

(def ^:private note-file-filter
  (reify java.io.FilenameFilter
    (accept [this dir name]
            (string/ends-with? name file-ext))))

(defn- value-or-default
  [config key-name default-value]
  (get config (keyword key-name) default-value))

(defn- slug
  [file]
  (string/lower-case (string/replace (.getName file) file-ext "")))

(defn- file-comparator
  [file1 file2]
  (compare (.getName file1) (.getName file2)))

(defn- list-notes
  [dir]
  (reverse (sort file-comparator (.listFiles dir note-file-filter))))

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
  (let [output-file (io/file output-dir (str (slug file) def-output-ext))]
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
      (= ndx 1)   (format "index.%s" def-output-ext)
      :else       (format "page-%d.%s" ndx def-output-ext))))

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
        notes       (map transformer page-notes) ;pmap
        next?       (not (empty? rest-notes))]
    (generate-page fmt-config vars output-dir page-index notes next?)))

(defn process-dir
  [notes-dir output-dir theme-dir notes-per-page noembed vars]
  (let [notes       (list-notes notes-dir)
        fmt-config  (freemarker/make-config theme-dir)
        processor   (partial page-processor fmt-config vars output-dir noembed)]
    (log/info (count notes) "notes were processed...")
    (let [pages (count (map processor (pages notes-per-page notes)))] ; pmap
      (log/info pages "pages were generated..."))))

(defn- copy-static
  [root from to]
  (mapv #(fs/copy-dir (io/file root %) to) from))

(defn generate
  [root-dir config]
  (let [general         (partial value-or-default (:general config {}))
        notes-dir       (io/file root-dir (general :notes def-notes))
        output-dir      (io/file root-dir (general :output def-output))
        theme-dir       (io/file root-dir (general :theme def-theme))
        cache-dir       (io/file root-dir (general :cache def-cache))
        noembed-cache   (io/file cache-dir noembed-cache-name)
        noembed         (noembed/make-noembed noembed-cache)
        static          (general :static def-static)
        notes-per-page  (general :notes-per-page def-notes-per-page)
        vars            (:vars config {})]
    (do
      (time (process-dir notes-dir output-dir theme-dir notes-per-page noembed vars))
      (.save noembed)
      (copy-static root-dir static output-dir))))