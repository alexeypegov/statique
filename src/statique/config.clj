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

(defn read-cache 
  [name]
  (->> (cache-file name)
       (u/read-edn)))

(def fm-renderer
  (delay
   (->> (general :theme-dir)
        (make-fm-renderer))))

(def notes-cache (delay (read-cache "notes")))

(def pages-cache (delay (read-cache "pages")))

(def feeds-cache (delay (read-cache "feeds")))

(def singles-cache (delay (read-cache "singles")))

(def notes-cache-file (delay (cache-file "notes")))

(def page-cache-file (delay (cache-file "pages")))

(def feeds-cache-file (delay (cache-file "feeds")))

(def singles-cache-file (delay (cache-file "singles")))

(def noembed-cache-file (delay (cache-file "noembed")))

(def noembed-cache
  (delay
    (u/file-cache @noembed-cache-file (fn [url] (noembed/fetch url)))))

(def markdown-extensions
  (delay (conj md/default-extensions (r/media-extension @noembed-cache))))

(def markdown-extensions-abs
  (delay (conj md/default-extensions (r/media-extension @noembed-cache (general :base-url)))))
