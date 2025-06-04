(ns statique.core
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [statique.util :as u]
            [statique.freemarker :as fm]
            [statique.markdown.noembed :as noembed]
            [statique.markdown.markdown :as md]
            [statique.markdown.renderers :as r]
            [statique.config :as cfg]
            [statique.pipeline :as pipeline])
  (:gen-class))

(def ^:private working-dir (u/working-dir))

(defn- blog-dir?
  [path]
  (let [file (io/as-file path)]
    (and
     (.isDirectory file)
     (.exists (io/file path cfg/config-name)))))

(defn- transformer
  [config noembed type object]
  (let [[notes-dir base-url] (cfg/get-generals config :notes-dir :base-url)]
    (as->
     (condp = type
       :absolute (conj md/default-extensions (r/media-extension noembed notes-dir base-url))
       :relative (conj md/default-extensions (r/media-extension noembed notes-dir))) ext
      (md/transform object :extensions ext))))

(defn- mk-noembed-cache
  [config]
  (let [file (cfg/get-cache-file config "noembed")]
    (u/file-cache file noembed/fetch)))

(defn- mk-renderer
  [config]
  (some->> (cfg/get-general config :theme-dir)
           (u/validate-dir)
           (fm/make-config)
           (partial fm/render)))

(defmulti reporter (fn [type _] type))
(defmethod reporter [:render :item] [_ key]
  (println "Rendering note:" key))
(defmethod reporter [:render :page] [_ index]
  (println "Rendering page:" index))
(defmethod reporter [:render :feed] [_ name]
  (println "Rendering feed:" name))
(defmethod reporter [:render :single] [_ name]
  (println "Rendering single page:" name))

(defn -main
  []
  (log/info "Statique" cfg/app-version)
  (if (blog-dir? working-dir)
    (let [[_ total-time] (u/timed
                          (let [config      (cfg/mk-config working-dir)
                                noembed     (mk-noembed-cache config)
                                transformer (partial transformer config noembed)
                                renderer    (mk-renderer config)]
                            (pipeline/build-site config noembed transformer renderer reporter)))]
      (log/info "Generation completed in" (u/format-time total-time))
      (flush))
    (log/error "Unable to find config file (" cfg/config-name ") at:" (.getAbsolutePath working-dir))))
