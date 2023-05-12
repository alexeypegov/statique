(ns statique.core
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.notes :as n]
            [statique.freemarker :as fm]
            [statique.markdown.noembed :as noembed]
            [statique.markdown.markdown :as md]
            [statique.markdown.renderers :as r]
            [statique.config :as cfg]
            [statique.static :as s])
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
  (let [notes-dir (cfg/with-general config :notes-dir)
        base-url  (cfg/with-general config :base-url)]
    (as->
     (condp = type
       :absolute (conj md/default-extensions (r/media-extension noembed notes-dir base-url))
       :relative (conj md/default-extensions (r/media-extension noembed notes-dir))) ext
      (md/transform object :extensions ext))))

(defn- mk-noembed-cache
  [config]
  (let [file (cfg/get-cache-file config "noembed")]
    (u/file-cache file #(noembed/fetch %))))

(defn- mk-renderer 
  [config]
  (some->> (cfg/with-general config :theme-dir)
           (u/validate-dir)
           (fm/make-config)
           (partial fm/render)))

(defn- file-writer
  [item]
  (when (:changed? item)
    (let [file (:target-file item)
          data (:rendered item)]
      (u/write-file file data)
      file)))

(defn- write-changed
  [items]
  (let [vals    (vals items)
        changed (filter :changed? vals)
        count   (count changed)]
    (when (> count 0)
      (doall (map file-writer changed))
      (println "Items written:" count)
      items)))

(defn- prepare-cache
  [r [k v]]
  (assoc r k (dissoc v :rendered :target-file :changed?)))

(defmulti reporter (fn [type _] type))
(defmethod reporter {:render :item} [_ key]
  (println "Rendering note:" key))
(defmethod reporter {:render :page} [_ index]
  (println "Rendering page:" index))
(defmethod reporter {:render :feed} [_ name]
  (println "Rendering feed:" name))
(defmethod reporter {:render :single} [_ name]
  (println "Rendering single page:" name))
  
(defn- render0
  [reporter config noembed]
  (let [item-cache  (cfg/get-cache config "items")
        transformer (partial transformer config noembed)
        renderer    (mk-renderer config)]
    (as-> item-cache $
      (n/generate-notes reporter config $ transformer renderer)
      (n/generate-singles reporter config $ transformer renderer))))

(defn- write-caches
  [config noembed items]
  (cfg/dump-cache config "items" items)
  (cfg/dump-cache config "noembed" (noembed :all)))
          
(defn -main
  []
  (printf "Statique %s\n\n" cfg/app-version)
  (if (blog-dir? working-dir)
    (let [config  (cfg/mk-config working-dir)
          noembed (mk-noembed-cache config)]
      (some->> (render0 reporter config noembed)
               write-changed
               (reduce prepare-cache {})
               (write-caches config noembed))
      (s/copy config)
      (flush))
    (printf "Unable to find config file (%s) in \"%s\"\n" cfg/config-name working-dir)))
