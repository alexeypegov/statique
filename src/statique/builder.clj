(ns statique.builder
  (:require [slingshot.slingshot :only [throw+]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [statique.markdown :as markdown]
            [statique.freemarker :as freemarker]
            [me.raynes.fs :as fs]))

(def file-ext           ".md")
(def def-notes          "notes/")
(def def-theme          "theme/")
(def def-output         "./out/")
(def def-output-ext     ".html")
(def def-encoding       "UTF-8")
(def def-static         "static/")
(def def-notes-per-page 5)

(def ^:private note-file-filter
  (reify java.io.FilenameFilter
    (accept [this dir name]
            (string/ends-with? name file-ext))))

(defn- log
  [& strings]
  (print (str (string/join "" strings) "\n")))

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
  [page-size col & {:keys [ndx] :or {ndx 0}}]
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
  (if (= create-dirs true) (.mkdirs (.getParentFile file)))
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
  [file]
  (assoc (markdown/transform (slurp file)) :file file))

(defn- prepare-page-file
  [output-dir ndx]
  (io/file output-dir (str
                        (if (zero? ndx)
                          "index"
                          (str "page-" (inc ndx)))
                        def-output-ext)))

(defn- generate-page
  [fmt-config vars output-dir ndx notes next?]
  (let [prev       (if (> ndx 0) (dec ndx) -1)
        next       (if next? (+ ndx 2) -1)
        to-html    (partial fmt->html fmt-config "index" (assoc vars :index ndx :prev prev :next next))
        file       (prepare-page-file output-dir ndx)]
    (log "Page " ndx "...")
    (write-to-file file (:content (to-html notes)))
    #_(generate-notes fmt-config vars output-dir notes)))

(defn- page-processor
  [fmt-config vars output-dir page]
  (let [page-notes  (:items page)
        page-index  (:index page)
        rest-notes  (:rest page)
        notes       (pmap transform-note page-notes)]
    (generate-page fmt-config vars output-dir page-index notes (not (empty? rest-notes)))))

(defn process-dir
  [notes-dir output-dir theme-dir notes-per-page vars]
  (let [notes       (list-notes notes-dir)
        fmt-config  (freemarker/make-config theme-dir)
        processor   (partial page-processor fmt-config vars output-dir)]
    (log (count notes) " notes were found...")
    (doall (pmap processor (pages notes-per-page notes)))))

(defn- copy-static
  [root from to]
  (mapv #(fs/copy-dir (io/file root %) to) from))

(defn generate
  [root-dir config]
  (let [general         (partial value-or-default (:general config {}))
        notes-dir       (io/file root-dir (general :notes def-notes))
        output-dir      (io/file root-dir (general :output def-output))
        theme-dir       (io/file root-dir (general :theme def-theme))
        static          (general :static def-static)
        notes-per-page  (general :notes-per-page def-notes-per-page)
                vars    (:vars config {})]
      (time (process-dir notes-dir output-dir theme-dir notes-per-page vars))
      (copy-static root-dir static output-dir)))