(ns statique.pipeline
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [statique.config :refer [get-general get-cache dump-cache]]
            [statique.notes :as notes]
            [statique.static :as static]
            [statique.util :as u]))

;; Pipeline Context
(defrecord PipelineContext [config noembed transformer renderer reporter options])

(defn create-context
  "Creates the pipeline context with all necessary dependencies"
  ([config noembed transformer renderer reporter]
   (create-context config noembed transformer renderer reporter {}))
  ([config noembed transformer renderer reporter options]
   (->PipelineContext config noembed transformer renderer reporter options)))

;; Pipeline Stages
(defprotocol PipelineStage
  "Protocol for pipeline stages"
  (execute [stage context state] "Execute the stage with given context and state"))

(defn- file-exists?
  [file]
  (.exists file))

(defn- sitemap-model
  [config state]
  (let [sitemap-item (partial notes/sitemap-item config)
        sitemap-items (filter some? (map sitemap-item (vals state)))]
    {:changed? (boolean (->> (vals state)
                             (filter :changed?)
                             (map sitemap-item)
                             (some some?)))
     :items sitemap-items
     :most-recent-date (->> sitemap-items
                            (map #(or (:updated %) (:date %)))
                            (filter some?)
                            (sort)
                            (last))}))

(defn- write-if-changed
  [{:keys [exists? crc32 write-file]
    :or {exists? file-exists?
         crc32 u/crc32
         write-file u/write-file}}
   target-file rendered]
  (when (or (not (exists? target-file))
            (not= (crc32 target-file) (crc32 rendered)))
    (write-file target-file rendered)
    true))

(defrecord LoadContentStage []
  PipelineStage
  (execute [_ context _state]
    (log/debug "Loading content...")
    (u/with-context context [config options]
      (let [ignore-cache? (:no-cache options)
            items-cache (get-cache config "items" ignore-cache?)
            ctx (select-keys context [:reporter :config :renderer :noembed :transformer])]
        (as-> items-cache $
          (notes/generate-notes ctx $)
          (notes/generate-singles ctx $))))))

(defrecord WriteOutputStage []
  PipelineStage
  (execute [_ _context state]
    (log/debug "Writing output...")
    (let [vals (vals state)
          changed (filter :changed? vals)
          count (count changed)]
      (when (> count 0)
        (doseq [item changed
                :let [file (:target-file item)
                      data (:rendered item)]
                :when (:changed? item)]
          (u/write-file file data))
        (log/info "Items written:" count))
      state)))

(defrecord CopyIndexStage []
  PipelineStage
  (execute [_ context state]
    (log/debug "Copying index if needed...")
    (u/with-context context [config]
      (when (get-general config :copy-last-as-index)
        (when-let [last-item (->> (vals state)
                                  (filter #(= :item (:type %)))
                                  (remove #(or (:deleted (:transformed %)) (:draft (:transformed %))))
                                  (sort #(compare
                                          (get-in %2 [:transformed :slug])
                                          (get-in %1 [:transformed :slug])))
                                  first)]
          (when (:changed? last-item)
            (let [slug (get-in last-item [:transformed :slug])
                  last-file (:target-file last-item)
                  index-file (io/file (.getParentFile last-file) "index.html")
                  data (:rendered last-item)]
              (u/write-file index-file data)
              (log/info "Writing note as index.html:" slug))))))
    state))

(defrecord GenerateSitemapStage []
  PipelineStage
  (execute [_ context state]
    (log/debug "Generating sitemap...")
    (u/with-context context [config renderer]
      (when-let [template (get-general config :sitemap-template)]
        (let [output-dir (get-general config :output-dir)
              target-file (io/file output-dir "sitemap.xml")
              model (sitemap-model config state)]
          (when (or (:changed? model) (not (file-exists? target-file)))
            (let [rendered (->> (select-keys model [:items :most-recent-date])
                                (renderer template)
                                u/check-render-error)]
              (when (write-if-changed {} target-file rendered)
                (log/info "Writing sitemap.xml")
                nil))))))
    state))

(defrecord CopyStaticStage []
  PipelineStage
  (execute [_ context _state]
    (log/debug "Copying static files...")
    (u/with-context context [config]
      (static/copy config))
    _state))

(defrecord PrepareCacheStage []
  PipelineStage
  (execute [_ _context state]
    (log/debug "Preparing cache...")
    (reduce (fn [r [k v]]
              (assoc r k (dissoc v :rendered :target-file :changed?)))
            {}
            state)))

(defrecord WriteCacheStage []
  PipelineStage
  (execute [_ context state]
    (log/debug "Writing caches...")
    (u/with-context context [config noembed]
      (dump-cache config "items" state)
      (dump-cache config "noembed" (noembed :all)))
    state))

;; Default Pipeline
(def default-pipeline
  [->LoadContentStage
   ->WriteOutputStage
   ->CopyIndexStage
   ->GenerateSitemapStage
   ->CopyStaticStage
   ->PrepareCacheStage
   ->WriteCacheStage])

;; Pipeline Execution
(defn run-pipeline
  "Runs the pipeline with given context and stages"
  ([context] (run-pipeline context default-pipeline))
  ([context stages]
   (let [stage-instances (map #(%) stages)]
     (reduce (fn [state stage]
               (execute stage context state))
             {}
             stage-instances))))

;; Main pipeline function
(defn build-site
  "Main function to build the static site"
  ([config noembed transformer renderer reporter]
   (build-site config noembed transformer renderer reporter {}))
  ([config noembed transformer renderer reporter options]
   (let [context (create-context config noembed transformer renderer reporter options)]
     (run-pipeline context))))
