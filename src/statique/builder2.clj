(ns statique.builder2
  )

(def ^:dynamic *fs* nil)
(def ^:dynamic *config* nil)
(def ^:dynamic *global-vars* nil)

(defmulti render-item #(class %))

(defmethod render-item statique.notes.Note
  [note]
  (log/debug "note" (:src note)))

(defmethod render-item statique.notes.Page
  [page]
  (log/debug "page" (:index page)))

(defn- render
  []
  (doall
    (map
      render-item
      (n/outdated-items
                  *fs*
                  (as-file :notes)
                  (output-dir)
                  (get-in *config* [:general :notes-per-page])))))

(defn build
  [blog-config]
  (let [config (merge-with into defaults/config blog-config)]
    (binding [*config*        config
              *fs*            (build-fs config)
              *global-vars*   (assoc (:vars config)
                                :statique statique-string
                                :statique-link statique-link)]
      (do
        (render)
        (.save *fs*)))))