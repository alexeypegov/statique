(ns statique.config
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [me.raynes.fs :as fs]
            [statique.util :as u]
            [statique.freemarker :as fm]
            [statique.markdown.markdown :as md]
            [statique.markdown.renderers :as r]
            [statique.markdown.noembed :as noembed]
            [yaml.core :as yaml]))

(def config-name                  "blog.yaml")
(def app-version                  (u/get-version 'statique))

(def ^:private convert-to-symbols true)

(def ^:private statique-string    (format "Statique %s" app-version))
(def ^:private statique-link      (format "<a href=\"https://github.com/alexeypegov/statique\">%s</a>" statique-string))

(def ^:private default-config     {:general {:notes-dir        			"notes/"
                                             :theme-dir        			"theme/"
                                             :singles-dir      			"singles/"
                                             :cache-dir        			"cache/"
                                             :note-template    			"note"
                                             :page-template    			"page"
                                             :index-page-name  			"index"
                                             :single-template  			"single"
                                             :output-dir       			"./out/"
                                             :notes-per-page  				10
                                             :date-format      			"yyyy-MM-dd"
                                             :tz               			"Europe/Moscow" ; TODO: make it local
                                             :base-url         			"/"
                                             :items-per-feed						10
                                             :feeds            			nil
                                             :copy             			nil}
                                   :vars    {}})

(defn- with-defaults
  [config]
  (merge-with into default-config config))

(defn- map-general-dirs
  [config working-dir]
  (->> (into {} (for [[k v] (:general config)] [k (if (and v (s/ends-with? (name k) "-dir")) (io/file working-dir v) v)]))
       (assoc config :general)))

(defn- append-statique-vars
  [config]
  (->> (assoc (:vars config)
              :statique      statique-string
              :statique-link statique-link)
       (assoc config :vars)))

(defn- parse-config
  [working-dir]
  (-> (io/file working-dir config-name)
      (yaml/from-file convert-to-symbols)
      (with-defaults)
      (map-general-dirs working-dir)
      (append-statique-vars)
      (assoc-in [:general :root-dir] working-dir)))

(defn- template-file 
  [theme-dir template-name]
  (fs/file theme-dir (format "%s.ftl" template-name)))

(def ^:private config
  (delay
   (->> (u/working-dir)
        (parse-config))))

(defn general 
  [& keys]
  (->> (conj keys :general)
       (get-in @config)))

(defn- make-fm-config 
  [theme-dir]
  (let [note-template (template-file theme-dir (general :note-template))]
    (if (fs/exists? note-template)
      (fm/make-config theme-dir)
      (u/exit -1 (format "Error: template '%s' was not found" note-template)))))

(defn- make-fm-renderer 
  [theme-dir]
  (some->> (u/validate-dir theme-dir)
           (make-fm-config)
           (partial fm/render)))

(def vars
  (delay (:vars @config)))

(defn cache-file 
  [name]
  (->> (format "%s.edn" name)
       (fs/file (general :cache-dir))))

(defn- skip-cache-prop-name
  [cache-name]
  (str "skip-" cache-name "-cache"))

(defn skip-cache?
  [cache-name]
  (->> (skip-cache-prop-name cache-name)
       (System/getProperty)
       (Boolean/valueOf)
       (boolean)))

(defn read-cache 
  [name]
  (if (skip-cache? name)
    		(do
        (printf "Skipping %s cache...\n" name)
        {})
      (->> (cache-file name)
       (u/read-edn))))

(defn write-cache
  [name data]
  (when (not (skip-cache? name))
    (printf "Writing %s cache...\n" name)
    (let [file (cache-file name)]
    		(u/write-file file data :data true))))

(def fm-renderer
  (delay
   (->> (general :theme-dir)
        (make-fm-renderer))))

(def notes-cache-name "notes")

(def pages-cache-name "pages")

(def feeds-cache-name "feeds")

(def singles-cache-name "singles")

(def noembed-cache-name "noembed")

(def notes-cache (delay (read-cache notes-cache-name)))

(def pages-cache (delay (read-cache pages-cache-name)))

(def feeds-cache (delay (read-cache feeds-cache-name)))

(def singles-cache (delay (read-cache singles-cache-name)))

(def noembed-cache-file (delay (cache-file noembed-cache-name)))

(def noembed-cache
  (delay 
    (if (skip-cache? noembed-cache-name)
      {}
    		(u/file-cache @noembed-cache-file (fn [url] (noembed/fetch url))))))

(def markdown-extensions
  (delay (conj md/default-extensions (r/media-extension @noembed-cache))))

(def markdown-extensions-abs
  (delay (conj md/default-extensions (r/media-extension @noembed-cache (general :base-url)))))
