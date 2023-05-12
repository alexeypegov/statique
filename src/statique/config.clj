(ns statique.config
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [me.raynes.fs :as fs]
            [statique.util :as u]
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
                                             :page-prefix           "page-"
                                             :single-template  			"single"
                                             :output-dir       			"./out/"
                                             :notes-per-page  			10
                                             :date-format      			"yyyy-MM-dd"
                                             :tz               			"Europe/Moscow" ; TODO: make it local
                                             :base-url         			"/"
                                             :items-per-feed				10
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
              :statique        statique-string
              :statique-link   statique-link
              :datetime-format "yyyy-MM-dd'T'HH:mm:ssXXX")
       (assoc config :vars)))

(defn mk-config
  [working-dir]
  (-> (io/file working-dir config-name)
      (yaml/from-file convert-to-symbols)
      (with-defaults)
      (map-general-dirs working-dir)
      (append-statique-vars)
      (assoc-in [:general :root-dir] working-dir)))

(defn with-general
  [cfg & keys]
  (->> (conj keys :general)
       (get-in cfg)))

(defn get-cache-file
  [cfg name]
  (let [dir       (with-general cfg :cache-dir)
        filename  (format "%s.edn" name)]
    (fs/file dir filename)))

(defn get-cache
  [cfg name]
  (u/read-edn (get-cache-file cfg name)))

(defn dump-cache
  [cfg name data]
  (let [file (get-cache-file cfg name)]
    (u/write-file file data :data true)
    (println "Cache written:" name)))