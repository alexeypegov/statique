(ns statique.notes
  (:require [me.raynes.fs :as fs]
            [statique.notes :as n]
            [statique.util :as u]
            [statique.markdown.markdown :as md]
            [statique.config :as cfg]
            [clj-uuid :as uuid]
            #_[clojure.tools.logging :as log :refer [log warn error info]]))

(def ^:private html-ext         ".html")
(def ^:private xml-ext          ".xml")
(def ^:private markdown-filter  (u/postfix-filter ".md"))

(defmulti page-filename identity)
(defmethod page-filename 1        [_] (str (cfg/general :index-page-name) html-ext))
(defmethod page-filename :default [i] (str "page-" i html-ext))

(defn- item-changed? 
  [{:keys [source-crc target-crc]} source-crc-current target-crc-current]
  (or
   (not= source-crc source-crc-current)
   (not= target-crc target-crc-current)))

(defn- make-item-map 
  [cache-fn source-file]
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

(defn- page-changed? 
  [{:keys [target-crc items-hash]} items target-crc-current items-hash-current]
  (or
   (not= target-crc target-crc-current)
   (not= items-hash items-hash-current)
   (true? (some :changed items))))

(defn- make-page 
  [{:keys [index items next?]}]
  (let [filename        (page-filename index)
        root-dir        (cfg/general :root-dir)
        output-dir      (cfg/general :output-dir)
        target-file     (fs/file output-dir filename)
        target-relative (u/relative-path root-dir target-file)
        target-crc      (u/crc32 target-file)
        items-hash      (hash (map :slug items))
        cached          (get @cfg/pages-cache index {})]
    (u/assoc? cached
              :type            :page
              :index           index
              :next?           next?
              :target-file     target-file
              :target-relative target-relative
              :changed         (page-changed? cached items target-crc items-hash)
              :target-crc      target-crc
              :items           items
              :items-hash      items-hash)))

(defn- feed-changed? 
  [{:keys [target-crc]} target-file target-crc-current]
  (or
   (not= target-crc target-crc-current)
   (not (fs/exists? target-file))))

(defn- make-feed 
  [name]
  (let [filename        (str name xml-ext)
        root-dir        (cfg/general :root-dir)
        output-dir      (cfg/general :output-dir)
        target-file     (fs/file output-dir filename)
        target-relative (u/relative-path root-dir target-file)
        target-crc      (u/crc32 target-file)
        cached          (get @cfg/feeds-cache name {})]
    (u/assoc? cached
              :type            :feed
              :name            name
              :target-relative target-relative
              :target-file     target-file
              :changed         (feed-changed? cached target-file target-crc)
              :target-crc      target-crc)))

(defn- format-dates 
  [date]
  (when (some? date)
		  (let [date-format (cfg/general :date-format)
  		      tz          (cfg/general :tz)
    		    parsed-date (u/parse-local-date date-format tz date)]
      {:rfc-822  (u/rfc-822 parsed-date)
     		:rfc-3339 (u/rfc-3339 parsed-date)})))

(defmulti check-error :status)
(defmethod check-error :ok [{:keys [result]}] result)
(defmethod check-error :error [{:keys [message]}] (u/exit -1 message))

(defn- render-to-file 
  [template-name vars target-file]
  (letfn [(write-file [data]
            (u/write-file target-file data)
            data)]
    (println (format "Rendering \"%s\"..." (u/relative-path (cfg/general :root-dir) target-file)))
    (->> (assoc vars :vars @cfg/vars)
         (@cfg/fm-renderer template-name)
         (check-error)
         (write-file))))

(defmulti ^:private render :type)

(defmethod render :note 
  [{:keys [target-file] :as m}]
  (let [template (cfg/general :note-template)]
    (render-to-file template {:note m} target-file)))

(defmethod render :page 
  [{:keys [target-file items index next?]}]
  (let [template (cfg/general :page-template)]
    (render-to-file template
                    {:items     items
                     :ndx       index
                     :next?     next?
                     :next-page (when next? (page-filename (inc index)))
                     :prev-page (when (> index 1) (page-filename (dec index)))}
                    target-file)))

(defmethod render :feed 
  [{:keys [name items target-file]}]
  (let [base-url (cfg/general :base-url)]
    (render-to-file name {:items items :base-url base-url :name name} target-file)))

(defmethod render :single 
  [{:keys [target-file] :as m}]
  (let [template (cfg/general :single-template)]
    (render-to-file template m target-file)))

(defmethod render :default 
  [m]
  (throw (IllegalArgumentException. (format "I don't know how to render \"%s\"!" m))))

(defn- write-caches 
  [file caches]
  (u/write-file file caches :data true))

(defn- make-note-cache 
  [result {:keys [source-relative] :as m}]
  (assoc result source-relative (select-keys m [:rendered :source-crc :target-crc :title :date :tags :rfc-822 :rfc-3339 :body :body-abs])))

(defn- dump-note-cache
  [notes]
  (->> (reduce make-note-cache {} notes)
       (write-caches @cfg/notes-cache-file)))

(defn- make-page-cache
  [result {:keys [index] :as page}]
  (assoc result index (select-keys page [:items-hash :target-crc :rendered])))

(defn- dump-page-cache
  [pages]
  (when (seq pages)
    (->> (reduce make-page-cache {} pages)
         (write-caches @cfg/page-cache-file))))

(defn- make-feed-cache 
  [result {:keys [name] :as feed}]
  (assoc result name (select-keys feed [:target-crc :rendered])))

(defn- dump-feed-cache
  [feeds]
  (when (seq feeds)
    (->> (reduce make-feed-cache {} feeds)
         (write-caches @cfg/feeds-cache-file))))

(defn- dump-noembed-cache 
  []
  (let [data (@cfg/noembed-cache :all)]
    (when-not (empty? data)
      (u/write-file @cfg/noembed-cache-file data :data true))))

(defn- make-note 
  [file]
  ; todo: extract cached fn to upper level
  (letfn [(cached [key] (get @cfg/notes-cache key {}))]
    (-> (make-item-map cached file)
        (assoc :type :note))))

(defn- target-crc 
  [{:keys [target-file]}]
  {:pre [(fs/exists? target-file)]}
  (u/crc32 target-file))

(defn- render-notes 
  [files]
  (->> (map make-note files)
       (u/prev-next #(assoc %1 :prev (:slug %3) :next (:slug %2)))
       (map #(if (:changed %)
               (let [text (slurp (:source-file %))]
                 (-> (merge % (md/transform text :extensions @cfg/markdown-extensions))
                     (assoc :body-abs (:body (md/transform text :extensions @cfg/markdown-extensions-abs)))))
               %))
       (map #(if (:changed %) (merge % (format-dates (:date %))) %))
       (remove :draft)
       (map #(if (:changed %) (assoc % :rendered (render %)) %))
       (map #(if (:changed %) (assoc % :target-crc (target-crc %)) %))))

(defn- render-feeds 
  [items]
  (let [changed (true? (some :changed items))]
    (->> (cfg/general :feeds)
       (map make-feed)
       (map #(if (or changed (:changed %)) (assoc % :rendered (render (assoc % :items items))) %))
       (map #(if (or changed (:changed %)) (assoc % :target-crc (target-crc %)) %)))))

(defn- render-pages 
  [pages]
  (->> (map make-page pages)
       (map #(if (:changed %) (assoc % :rendered (render %)) %))
       (map #(if (:changed %) (assoc % :target-crc (target-crc %)) %))))

(defn- render-pages-if-enabled
  [notes]
  (let [npp (cfg/general :notes-per-page)]
    (when (> npp 0)
      (->> (u/paged-seq npp notes)
           (render-pages)))))

(defn generate-notes-and-pages
  []
  (when-let [notes-dir (u/validate-dir (cfg/general :notes-dir))]
    (let [files (reverse (u/sorted-files notes-dir markdown-filter))
          notes (render-notes files)
          pages (render-pages-if-enabled notes)
          ipf   (cfg/general :items-per-feed)
          feeds (render-feeds (take ipf notes))]
      (dump-note-cache notes)
      (dump-feed-cache feeds)
      (dump-page-cache pages)
      (dump-noembed-cache))))

(defn- make-single 
  [file]
  (letfn [(cached [key] (get @cfg/singles-cache key {}))]
    (assoc (make-item-map cached file)
           :type :single)))

(defn- render-singles 
  [files]
  (->> (map make-single files)
       (map #(if (:changed %) (merge % (md/transform-file (:source-file %))) %))
       (map #(if (:changed %) (assoc % :rendered (render %)) %))
       (map #(if (:changed %) (assoc % :target-crc (target-crc %)) %))))

(defn- make-singles-cache 
  [result {:keys [source-relative] :as single}]
  (assoc result source-relative (select-keys single [:rendered :source-crc :target-crc])))

(defn- dump-singles-cache
  [singles]
  (when (seq singles)
		  (->> (reduce make-singles-cache {} singles)
		       (write-caches @cfg/singles-cache-file))))

(defn generate-singles
  []
  (let [dir 				(cfg/general :singles-dir)
        files 		(u/list-files dir markdown-filter)
        singles (render-singles files)]
    (dump-singles-cache singles)))