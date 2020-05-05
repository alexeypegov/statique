(ns statique.notes
  (:require [clojure.string :as s]
            [me.raynes.fs :as fs]
            [statique.notes :as n]
            [statique.util :as u]
            [statique.freemarker :as fm]
            [statique.markdown.markdown :as md]
            [statique.config :as cfg]
            [clj-uuid :as uuid])
  (:use [clojure.tools.logging :only [log warn error info]]))

(def ^:private html-ext         ".html")
(def ^:private xml-ext          ".xml")
(def ^:private note-template    "note")
(def ^:private page-template    "index")
(def ^:private single-template  "single")
(def ^:private markdown-filter  (u/postfix-filter ".md"))

(defmulti page-filename identity)
(defmethod page-filename 1        [_] (str "index" html-ext))
(defmethod page-filename :default [i] (str "page-" i html-ext))

(defn- item-changed? [{:keys [source-crc target-crc]} source-crc-current target-crc-current]
  (or
   (not= source-crc source-crc-current)
   (not= target-crc target-crc-current)))

(defn- make-item-map [cache-fn source-file]
  (let [root-dir        (cfg/general :root-dir)
        source-relative (u/relative-path root-dir source-file)
        slug            (u/slug source-file)
        target-filename (str slug html-ext)
        output-dir      (cfg/general :output-dir)
        target-file     (fs/file output-dir target-filename)
        target-relative (u/relative-path root-dir target-file)
        absolute-link   (str "/" slug html-ext)
        target-crc      (u/crc32 target-file)
        source-crc      (u/crc32 source-file)
        cached          (cache-fn source-relative)]
    (assoc cached
           :source-file     source-file
           :source-relative source-relative
           :target-file     target-file
           :target-relative target-relative
           :slug            slug
           :link            absolute-link
           :uuid            (uuid/v3 uuid/+namespace-url+ absolute-link)
           :changed         (item-changed? cached source-crc target-crc)
           :source-crc      source-crc
           :target-crc      target-crc)))

(defn- page-changed? [{:keys [target-crc items-hash items]} target-crc-current items-hash-current]
  (or
   (not= target-crc target-crc-current)
   (not= items-hash items-hash-current)
   (true? (some :changed items))))

(defn- make-page [{:keys [index items] :as page}]
  (let [filename        (page-filename index)
        root-dir        (cfg/general :root-dir)
        output-dir      (cfg/general :output-dir)
        target-file     (fs/file output-dir filename)
        target-relative (u/relative-path root-dir target-file)
        target-crc      (u/crc32 target-file)
        items-hash      (hash (map :slug items))
        cached          (get-in @cfg/notes-cache [:pages index] {})]
    (u/assoc? cached
              :type            :page
              :index           index
              :target-file     target-file
              :target-relative target-relative
              :changed         (page-changed? cached target-crc items-hash)
              :target-crc      target-crc
              :items           items
              :items-hash      items-hash)))

(defn- feed-changed? [{:keys [target-crc]} target-file target-crc-current]
  (or
   (not= target-crc target-crc-current)
   (not (fs/exists? target-file))))

(defn- make-feed [name]
  (let [filename        (str name xml-ext)
        root-dir        (cfg/general :root-dir)
        output-dir      (cfg/general :output-dir)
        target-file     (fs/file output-dir filename)
        target-relative (u/relative-path root-dir target-file)
        target-crc      (u/crc32 target-file)
        cached          (get-in @cfg/notes-cache [:feeds name] {})]
    (u/assoc? cached
              :type            :feed
              :name            name
              :target-relative target-relative
              :target-file     target-file
              :changed         (feed-changed? cached target-file target-crc)
              :target-crc      target-crc)))

(defn- format-dates [date]
  (let [date-format (cfg/general :date-format)
        tz          (cfg/general :tz)
        parsed-date (u/parse-local-date date-format tz date)]
    {:rfc-822  (u/rfc-822 parsed-date)
     :rfc-3339 (u/rfc-3339 parsed-date)}))

(defmulti check-error :status)
(defmethod check-error :ok [{:keys [result]}] result)
(defmethod check-error :error [{:keys [message model]}]
  (error "Error rendering model" model)
  (error message)
  (u/exit -1))

(defn- render-to-file [template-name vars target-file]
  (letfn [(write-file [data]
            (u/write-file target-file data)
            data)]
    (info (format "Rendering \"%s\"..." (u/relative-path (cfg/general :root-dir) target-file)))
    (->> (assoc vars :vars @cfg/vars)
         (@cfg/fm-renderer template-name)
         (check-error)
         (write-file))))

(defmulti ^:private render :type)
(defmethod render :note [{:keys [target-file] :as m}]
  (render-to-file note-template {:note m} target-file))
(defmethod render :page [{:keys [target-file items index next?] :as page}]
  (render-to-file page-template {:items items :ndx index :next? next?} target-file))
(defmethod render :feed [{:keys [name items target-file] :as feed}]
  (render-to-file name {:items items :base-url (cfg/general :base-url) :name name} target-file))
(defmethod render :single [{:keys [target-file] :as m}]
  (render-to-file single-template m target-file))
(defmethod render :default [m]
  (throw (IllegalArgumentException. (format "I don't know how to render \"%s\"!" m))))

(defn- make-note-cache [result {:keys [note source-relative] :as m}]
  (assoc result source-relative (select-keys m [:rendered :source-crc :target-crc :title :date :rfc-822 :rfc-3339])))

(defn- make-feed-cache [feeds]
  (when feeds
    (reduce (fn [result feed]
              (assoc result (:name feed) (select-keys feed [:target-crc :rendered])))
            {}
            feeds)))

(defn- make-cache [{:keys [pages notes feeds] :as result} page]
  ; todo: avoid writing caches if nothing has changed
  (as-> result $
    (assoc $ :pages (merge pages {(:index page) (select-keys page [:items-hash :target-crc :rendered])}))
    (assoc $ :notes (merge notes (reduce make-note-cache {} (:items page))))
    (u/assoc? $ :feeds (merge feeds (make-feed-cache (:feeds page))))))

(defn- write-caches [file caches]
  (u/write-file file caches :data true))

(defn- make-note [file]
  (letfn [(cached [key] (get-in @cfg/notes-cache [:notes key] {}))]
    (assoc (make-item-map cached file)
           :type :note)))

(defn- target-crc [{:keys [target-file]}]
  {:pre [(fs/exists? target-file)]}
  (u/crc32 target-file))

(defn- render-notes [files]
  (->> (map make-note files)
       (map #(if (:changed %) (merge % (md/transform-file (:source-file %))) %))
       (map #(if (:changed %) (merge % (format-dates (:date %))) %))
       (remove :draft)
       (map #(if (:changed %) (assoc % :rendered (render %)) %))
       (map #(if (:changed %) (assoc % :target-crc (target-crc %)) %))))

(defn- render-feeds [{:keys [changed items] :as page}]
  (->> (cfg/general :feeds)
       (map make-feed)
       (map #(if (or changed (:changed %)) (assoc % :rendered (render (assoc % :items items))) %))
       (map #(if (or changed (:changed %)) (assoc % :target-crc (target-crc %)) %))))

(defn- render-pages [s]
  (->> (map make-page s)
       (map #(if (:changed %) (assoc % :rendered (render %)) %))
       (map #(if (:changed %) (assoc % :target-crc (target-crc %)) %))
       (map #(if (= 1 (:index %)) (assoc % :feeds (render-feeds %)) %))))

(defn generate []
  (when-let [notes-dir (u/validate-dir (cfg/general :notes-dir))]
    (dorun
     (->> (u/sorted-files notes-dir markdown-filter)
          (reverse)
          (render-notes)
          (u/paged-seq (cfg/general :notes-per-page))
          (render-pages)
          (reduce make-cache {})
          (write-caches (cfg/notes-cache-file))))))

(defn- make-single [file]
  (letfn [(cached [key] (get @cfg/singles-cache key {}))]
    (assoc (make-item-map cached file)
           :type :single)))

(defn- render-singles [files]
  (->> (map make-single files)
       (map #(if (:changed %) (merge % (md/transform-file (:source-file %))) %))
       (map #(if (:changed %) (assoc % :rendered (render %)) %))
       (map #(if (:changed %) (assoc % :target-crc (target-crc %)) %))))

(defn- make-singles-cache [result {:keys [source-relative] :as single}]
  (assoc result source-relative (select-keys single [:rendered :source-crc :target-crc])))

(defn generate-singles []
  (dorun
   (->> (u/list-files (cfg/general :singles-dir) markdown-filter)
        (render-singles)
        (reduce make-singles-cache {})
        (write-caches (cfg/singles-cache-file)))))
