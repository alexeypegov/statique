(ns statique.config
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [me.raynes.fs :as fs]
            [clojure.tools.logging :as log]
            [statique.util :as u]
            [yaml.core :as yaml]))

(def config-name                  "blog.yaml")
(def app-version                  (u/get-version 'statique))
(def ^:private convert-to-symbols true)
(def ^:private statique           "<a href=\"https://github.com/alexeypegov/statique\">Statique</a>")
(def ^:private default-config     {:general {:notes-dir        			"notes/"
                                             :theme-dir        			"theme/"
                                             :singles-dir      			"singles/"
                                             :cache-dir        			"cache/"
                                             :note-template    			"note"
                                             :page-template    			"page"
                                             :index-page-name  			"index"
                                             :page-prefix           "page-"
                                             :single-template  			"single"
                                             :copy-last-as-index    false
                                             :sitemap-template      nil
                                             :output-dir       			"./out/"
                                             :notes-per-page  			10
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
              :statique statique)
       (assoc config :vars)))

(defn mk-config
  ([working-dir] (mk-config working-dir config-name))
  ([working-dir config-filename]
   (-> (io/file working-dir config-filename)
       (yaml/from-file convert-to-symbols)
       (with-defaults)
       (map-general-dirs working-dir)
       (append-statique-vars)
       (assoc-in [:general :root-dir] working-dir))))

(defn get-general
  [cfg & keys]
  (->> (conj keys :general)
       (get-in cfg)))

(defn get-generals [cfg & keys]
  (mapv #(get-general cfg %) keys))

(defn get-cache-file
  [cfg name]
  (let [dir       (get-general cfg :cache-dir)
        filename  (format "%s.edn" name)]
    (fs/file dir filename)))

(defn get-cache
  ([cfg name] (get-cache cfg name false))
  ([cfg name ignore-cache?]
   (if ignore-cache?
     {}
     (u/read-edn (get-cache-file cfg name)))))

(defn dump-cache
  [cfg name data]
  (when (seq data)
    (let [file (get-cache-file cfg name)]
      (u/write-file file data :data true)
      (log/info "Cache written:" name))))
