(ns statique.notes
  (:require [statique.items :refer [item-seq]]
            [statique.config :refer [with-general]]
            [statique.util :as u]
            [me.raynes.fs :as fs]
            [clj-uuid :as uuid]))

(def ^:private markdown-filter  (u/postfix-filter ".md"))
(def ^:private html-ext         ".html")
(def ^:private md-ext           ".md")
(def ^:private xml-ext          ".xml")

(defn- notes-dir [cfg] (with-general cfg :notes-dir))
(defn- output-dir [cfg] (with-general cfg :output-dir))

(defn- page-filename
  [config index]
  (let [index-name   (with-general config :index-page-name)
        page-prefix  (with-general config :page-prefix)]
    (if (= index 1)
      (str index-name html-ext)
      (str page-prefix index html-ext))))

(defn- items-changed?
  [keys items]
  (->> (select-keys items keys)
       vals
       (some :changed?)
       true?))

(defn- render-item
  [props config renderer template transformed]
  (let [tpl  (with-general config template)
        vars (:vars config)]
    (->> (assoc props :vars vars)
         (merge transformed)
         (renderer tpl)
         u/check-render-error)))

(defn item-transform
  [transformer type source-file slug]
  (let [text        (slurp source-file)
        transformed (transformer type text)
        link        (str "/" slug html-ext)
        uuid        (uuid/v3 uuid/+namespace-url+ link)]
    (assoc transformed
           :slug slug
           :link link
           :uuid uuid)))

(defprotocol Handler
  (id [this])
  (changed? [this])
  (populate [this transformed])
  (transform [this transformer])
  (render [this props renderer transformed]))

(deftype ItemHandler [config slug count template source-file target-file source-crc target-crc cached]
  Handler
  (id [_] slug)
  (changed? [_]
    (or
     (not= source-crc   (:source-crc cached))
     (not= target-crc   (:target-crc cached))
     (not= count        (:count cached))))
  (populate [_ transformed]
    (u/?assoc {}
     :source-crc  source-crc
     :target-file target-file
     :transformed transformed
     :count       count))
  (transform [_ transformer]
    (item-transform transformer :relative source-file slug))
  (render [_ props renderer transformed]
    (render-item props config renderer template transformed)))
  
(deftype PageHandler [config index slugs items target-file items-hash target-crc cached]
  Handler
  (id [_] index)
  (changed? [_]
    (or
     (not= target-crc (:target-crc cached))
     (not= items-hash (:items-hash cached))
     (items-changed? slugs items)))
  (populate [_ _]
    {:items-hash  items-hash
     :target-file target-file})
  (transform [_ _]
    (map #(:transformed (get items %)) slugs))
  (render [_ props renderer transformed]
    (let [template  (with-general config :page-template)
          vars      (:vars config)
          index     (:index props)
          next?     (:next? props)
          next-page (when next? (page-filename config (inc index)))
          prev-page (when (> index 1) (page-filename config (dec index)))]
      (->> (assoc props
                  :ndx       index
                  :next-page next-page
                  :prev-page prev-page
                  :vars      vars
                  :items     transformed)
           (renderer template)
           u/check-render-error))))

(deftype FeedHandler [config name slugs items target-file items-hash target-crc cached]
  Handler
  (id [_] name)
  (changed? [_]
    (or
     (not= target-crc (:target-crc cached))
     (not= items-hash (:items-hash cached))
     (items-changed? slugs items)))
  (populate [_ _]
    {:items-hash  items-hash
     :target-file target-file})
  (transform [_ transformer]
    (let [transform (partial item-transform transformer :absolute)]
      (letfn [(tr [slug]
                (let [notes-dir   (notes-dir config)
                      filename    (str slug md-ext)
                      source-file (fs/file notes-dir filename)]
                  (transform source-file slug)))]
        (map tr slugs))))
  (render [_ props renderer transformed]
    (let [base-url (with-general config :base-url)
          vars     (:vars config)]
      (->> (assoc props
                  :items    transformed
                  :base-url base-url
                  :vars     vars)
           (renderer name)
           u/check-render-error))))

(defmulti mk-handler (fn [item & _] (:type item)))

(defmethod mk-handler :item [item {:keys [config items]}]
  (let [notes-dir   (notes-dir config)
        output-dir  (output-dir config)
        slug        (:slug item)
        count       (:count item)
        real-slug   (or (:real-slug item) slug)
        target-file (fs/file output-dir (str slug html-ext))
        source-file (fs/file notes-dir (str real-slug md-ext))
        source-crc  (u/crc32 source-file)
        target-crc  (u/crc32 target-file)
        cached      (get items slug {})]
    (->ItemHandler config slug count :note-template source-file target-file source-crc target-crc cached)))

(defmethod mk-handler :page [page {:keys [config items]}]
  (let [index       (:index page)
        slugs       (:items page)
        output-dir  (output-dir config)
        filename    (page-filename config index)
        target-file (fs/file output-dir filename)
        target-crc  (u/crc32 target-file)
        items-hash  (hash slugs)
        cached      (get items index {})]
    (->PageHandler config index slugs items target-file items-hash target-crc cached)))

(defmethod mk-handler :feed [feed {:keys [config items]}]
  (let [name        (:name feed)
        slugs       (:slugs feed)
        filename    (str name xml-ext)
        output-dir  (output-dir config)
        target-file (fs/file output-dir filename)
        target-crc  (u/crc32 target-file)
        items-hash  (hash slugs)
        cached      (get items name {})]
    (->FeedHandler config name slugs items target-file items-hash target-crc cached)))
    
(defmethod mk-handler :single [item {:keys [config items]}]
  (let [singles-dir (with-general config :singles-dir)
        output-dir  (output-dir config)
        slug        (:slug item)
        source-file (fs/file singles-dir (str slug md-ext))
        target-file (fs/file output-dir (str slug html-ext))
        source-crc  (u/crc32 source-file)
        target-crc  (u/crc32 target-file)
        cached      (get items slug {})]
    (->ItemHandler config slug nil :single-template source-file target-file source-crc target-crc cached)))

(defn- process-item [reporter transformer renderer {:keys [items] :as ctx} item]
  (let [type      (:type item)
        handler   (mk-handler item ctx)
        changed?  (changed? handler)]
    (if changed?
      (do
        (reporter [:render type] (id handler))
        (let [transformed    (transform handler transformer)
              rendered       (render handler item renderer transformed)
              new-target-crc (u/crc32 rendered)]
          (as-> (populate handler transformed) $
            (assoc $ :target-crc new-target-crc)
            (assoc $ :changed? changed?)
            (assoc $ :rendered rendered)
            (assoc $ :type type)
            (assoc items (id handler) $)
            (assoc ctx :items $))))
      ctx)))

(defmulti process (fn [& args] (:type (last args))))

(defmethod process :item [& args]
  (apply process-item args))

(defmethod process :page [& args]
  (apply process-item args))

(defmethod process :feed [reporter transformer renderer {:keys [config] :as context} {slugs :items}]
  (let [feeds (with-general config :feeds)
        proc  (partial process-item reporter transformer renderer)]
    (->> (map #(assoc {}
                      :type :feed
                      :name %
                      :slugs slugs)
              feeds)
         (reduce proc context))))

(defmethod process :single [& args]
  (apply process-item args))

(defn- prev-next
  [add-cnt coll]
  (let [cnt   (count (filter #(= :item (:type %)) coll))
        index (atom (+ 1 cnt))
        _cnt  (if add-cnt cnt nil)]
    (u/prev-next
     #(= :item (:type %))
     #(assoc %1
             :count _cnt ; count is added for pageless renders only since it might cause notes to be rerendered
             :index (swap! index dec)
             :prev  (:slug %3)
             :next  (:slug %2))
     coll)))

(defn generate-notes
  [reporter config transformer renderer items-cache]
  (when-let [notes-dir (u/validate-dir (with-general config :notes-dir))]
    (let [page-size    (with-general config :notes-per-page)
          feed-size    (with-general config :items-per-feed)
          files        (reverse (u/sorted-files notes-dir markdown-filter))
          slugs        (map u/slug files)
          proc         (partial process reporter transformer renderer)
          context      {:config config
                        :items  items-cache}
          pageless     (= page-size 0)]
     (->> (item-seq page-size feed-size slugs)
          (prev-next pageless)
          (reduce proc context)
          :items))))

(defn generate-singles
  [reporter config transformer renderer items-cache]
  (let [singles-dir (with-general config :singles-dir)
        files (u/list-files singles-dir markdown-filter)
        slugs (map u/slug files)
        proc (partial process reporter transformer renderer)
        context {:config config
                 :items  items-cache}]
    (->> (item-seq :single slugs)
         (reduce proc context)
         :items)))

(defmulti sitemap-item (fn [_ props] (:type props)))
(defmethod sitemap-item :page [_ _] nil)
(defmethod sitemap-item :feed [_ _] nil)
(defmethod sitemap-item :default [config props]
  (let [base-url     (with-general config :base-url)
        transformed  (:transformed props)]
    (assoc transformed
           :loc (str base-url (:slug transformed) html-ext))))
  