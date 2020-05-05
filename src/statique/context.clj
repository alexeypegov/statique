(ns statique.context
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [me.raynes.fs :as fs]
            [statique.util :as u]
            [statique.freemarker :as fm]
            [yaml.core :as yaml]))

(def config-name                  "blog.yaml")
(def app-version                  (u/get-version 'statique))

(def ^:private convert-to-symbols true)


(def ^:private statique-string    (format "Statique %s" app-version))
(def ^:private statique-link      (format "<a href=\"https://github.com/alexeypegov/statique\">%s</a>" statique-string))

(def ^:private default-config     {:general {:notes-dir       "notes/"
                                             :pages-dir       nil
                                             :theme-dir       "theme/"
                                             :output-dir      "./out/"
                                             :cache-dir       "cache/"
                                             :notes-per-page  10
                                             :date-format     "yyyy-MM-dd"
                                             :tz              "Europe/Moscow"
                                             :base-url        "/"
                                             :feeds           ["rss"]
                                             :pages           nil
                                             :copy            nil
                                             :keep            nil}
                                   :vars {}})


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

(defn- template-file [theme-dir template-name]
  (fs/file theme-dir (format "%s.ftl" template-name)))

(defn- make-fm-config [theme-dir]
  (let [note-template (template-file theme-dir "note")]
    (if (fs/exists? note-template)
      (fm/make-config theme-dir)
      (u/exit -1 (format "Error: template '%s' was not found" note-template)))))

(defn- make-fm-renderer [theme-dir]
  (some->> (u/validate-dir theme-dir)
           (make-fm-config)
           (partial fm/render)))

(defonce config
  (->> (u/working-dir)
       (parse-config)))

(defn- general [& keys]
  (->> (conj keys :general)
       (get-in config)))

(defn- cache-file [name]
  (->> (format "%s.edn" name)
       (fs/file (general :cache-dir))))

(defn- read-cache [name]
  (->> (cache-file name)
       (u/read-edn)))

(defonce vars
  (:vars config))

(defonce base-url
  (general :base-url))

(def root-dir
  (general :root-dir))

(defonce output-dir
  (general :output-dir))

(defonce notes-dir
  (general :notes-dir))

(defonce pages-dir
  (general :pages-dir))

(defonce theme-dir
  (general :theme-dir))

(defonce notes-per-page
  (general :notes-per-page))

(defonce date-format
  (general :date-format))

(defonce tz
  (general :tz))

(defonce base-url
  (general :base-url))

(defonce feeds
  (general :feeds))

(defonce fm-renderer
  (->> (general :theme-dir)
       (make-fm-renderer)))

(defonce notes-cache
  (read-cache "notes"))

(defonce notes-cache-file
  (cache-file "notes"))

(defonce copy
  (general :copy))

(defonce singles-dir
  (general :singles-dir))

(defonce singles-cache
  (read-cache "singles"))

(defonce singles-cache-file
  (cache-file "singles"))
