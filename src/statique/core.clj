(ns statique.core
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli]
            [statique.util :as u]
            [statique.freemarker :as fm]
            [statique.markdown.noembed :as noembed]
            [statique.markdown.markdown :as md]
            [statique.markdown.renderers :as r]
            [statique.config :as cfg]
            [statique.pipeline :as pipeline])
  (:gen-class))

(def ^:private working-dir (u/working-dir))

(def cli-options
  [["-d" "--debug" "Enable debug output"]
   ["-n" "--no-cache" "Ignore items cache (force regeneration)"]
   ["-c" "--config PATH" "Path to config file"
    :default "blog.yaml"]
   ["-h" "--help" "Show help"]])

(defn- blog-dir?
  ([path] (blog-dir? path cfg/config-name))
  ([path config-name]
   (let [file (io/as-file path)]
     (and
      (.isDirectory file)
      (.exists (io/file path config-name))))))

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

(defn- set-debug-logging!
  []
  (-> (org.slf4j.LoggerFactory/getLogger org.slf4j.Logger/ROOT_LOGGER_NAME)
      (.setLevel ch.qos.logback.classic.Level/DEBUG)))

(defn- show-help
  [options-summary]
  (println "Statique - Static site generator")
  (println)
  (println "Usage: statique [options]")
  (println)
  (println "Options:")
  (println options-summary))

(defn -main
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (show-help summary)
      
      errors
      (do
        (doseq [error errors]
          (println "Error:" error))
        (show-help summary)
        (System/exit 1))
      
      :else
      (let [config-path (:config options)
            config-file (io/file config-path)
            actual-working-dir (if (= config-path "blog.yaml")
                                 working-dir
                                 (.getParentFile (io/file config-path)))
            config-name (.getName config-file)]
        (when (:debug options)
          (set-debug-logging!))
        
        (log/info "Statique" cfg/app-version)
        
        (if (blog-dir? actual-working-dir config-name)
          (let [[_ total-time] (u/timed
                                (let [config      (cfg/mk-config actual-working-dir config-name)
                                      no-cache?   (:no-cache options)
                                      noembed     (mk-noembed-cache config)
                                      transformer (partial transformer config noembed)
                                      renderer    (mk-renderer config)
                                      options-map {:no-cache no-cache?}]
                                  (pipeline/build-site config noembed transformer renderer reporter options-map)))]
            (log/info "Generation completed in" (u/format-time total-time))
            (flush))
          (log/error "Unable to find config file (" config-name ") at:" (.getAbsolutePath actual-working-dir)))))))
