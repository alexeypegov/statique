(ns statique.notes
  (:require [statique.items :refer [item-seq]]
            [statique.config :refer [get-general get-generals]]
            [statique.util :as u]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [clj-uuid :as uuid]))

(def ^:private markdown-filter  (u/postfix-filter ".md"))
(def ^:private html-ext         ".html")
(def ^:private md-ext           ".md")
(def ^:private xml-ext          ".xml")

(defn- notes-dir [cfg] (get-general cfg :notes-dir))
(defn- output-dir [cfg] (get-general cfg :output-dir))

(defn- file-with-crc
  [dir filename]
  (let [file (fs/file dir filename)]
    {:file file :crc (u/crc32 file)}))

(defn- render-context
  [config props & kvs]
  (-> props
      (assoc :vars (:vars config))
      (merge (apply hash-map kvs))))

(defn- page-filename
  [config index]
  (let [[index-name page-prefix] (get-generals config :index-page-name :page-prefix)]
    (if (= index 1)
      (str index-name html-ext)
      (str page-prefix index html-ext))))

(defn- items-changed?
  [keys items]
  (->> (select-keys items keys)
       vals
       (some :changed?)
       true?))

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

(deftype ItemHandler [config slug count template source target prev next cached]
  Handler
  (id [_] slug)
  (changed? [_]
    (or
     (not= (:crc source)   (:source-crc cached))
     (not= (:crc target)   (:target-crc cached))
     (not= count           (:count cached))
     (not= prev            (:prev cached))
     (not= next            (:next cached))))
  (populate [_ transformed]
    (u/?assoc {}
              :source-crc  (:crc source)
              :target-file (:file target)
              :transformed transformed
              :count       count
              :prev        prev
              :next        next))
  (transform [_ transformer]
    (item-transform transformer :relative (:file source) slug))
  (render [_ props renderer transformed]
    (let [tpl (get-general config template)]
      (->> (render-context config props)
           (merge transformed)
           (renderer tpl)
           u/check-render-error))))

(deftype PageHandler [config index slugs items target items-hash cached]
  Handler
  (id [_] index)
  (changed? [_]
    (or
     (not= (:crc target) (:target-crc cached))
     (not= items-hash    (:items-hash cached))
     (items-changed? slugs items)))
  (populate [_ _]
    {:items-hash  items-hash
     :target-file (:file target)})
  (transform [_ _]
    (map #(:transformed (get items %)) slugs))
  (render [_ props renderer transformed]
    (let [template  (get-general config :page-template)
          index     (:index props)
          next?     (:next? props)
          next-page (when next? (page-filename config (inc index)))
          prev-page (when (> index 1) (page-filename config (dec index)))]
      (->> (render-context config props
                           :ndx       index
                           :next-page next-page
                           :prev-page prev-page
                           :items     transformed)
           (renderer template)
           u/check-render-error))))

(deftype FeedHandler [config name slugs items target items-hash cached]
  Handler
  (id [_] name)
  (changed? [_]
    (or
     (not= (:crc target) (:target-crc cached))
     (not= items-hash    (:items-hash cached))
     (items-changed? slugs items)))
  (populate [_ _]
    {:items-hash  items-hash
     :target-file (:file target)})
  (transform [_ transformer]
    (let [transform (partial item-transform transformer :absolute)]
      (letfn [(tr [slug]
                  (let [notes-dir   (notes-dir config)
                        filename    (str slug md-ext)
                        source-file (fs/file notes-dir filename)]
                    (transform source-file slug)))]
        (map tr slugs))))
  (render [_ props renderer transformed]
    (let [base-url (get-general config :base-url)]
      (->> (render-context config props
                           :items    transformed
                           :base-url base-url)
           (renderer name)
           u/check-render-error))))

(defmulti mk-handler (fn [_ item _] (:type item)))

(defmethod mk-handler :item [ctx item items]
  (u/with-context ctx [config]
    (let [notes-dir   (notes-dir config)
          output-dir  (output-dir config)
          slug        (:slug item)
          count       (:count item)
          prev        (:prev item)
          next        (:next item)
          real-slug   (or (:real-slug item) slug)
          source      (file-with-crc notes-dir (str real-slug md-ext))
          target      (file-with-crc output-dir (str slug html-ext))
          cached      (get items slug {})]
      (->ItemHandler config slug count :note-template source target prev next cached))))

(defmethod mk-handler :page [ctx page items]
  (u/with-context ctx [config]
    (let [index       (:index page)
          slugs       (:items page)
          output-dir  (output-dir config)
          filename    (page-filename config index)
          target      (file-with-crc output-dir filename)
          items-hash  (hash slugs)
          cached      (get items index {})]
      (->PageHandler config index slugs items target items-hash cached))))

(defmethod mk-handler :feed [ctx feed items]
  (u/with-context ctx [config]
    (let [name        (:name feed)
          slugs       (:slugs feed)
          filename    (str name xml-ext)
          output-dir  (output-dir config)
          target      (file-with-crc output-dir filename)
          items-hash  (hash slugs)
          cached      (get items name {})]
      (->FeedHandler config name slugs items target items-hash cached))))

(defmethod mk-handler :single [ctx item items]
  (u/with-context ctx [config]
    (let [singles-dir (get-general config :singles-dir)
          output-dir  (output-dir config)
          slug        (:slug item)
          source      (file-with-crc singles-dir (str slug md-ext))
          target      (file-with-crc output-dir (str slug html-ext))
          cached      (get items slug {})]
      (->ItemHandler config slug nil :single-template source target nil nil cached))))

(u/defnc- process-item [reporter transformer renderer] [items-cache item]
  (let [type      (:type item)
        handler   (mk-handler $ctx item items-cache)
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
            (assoc items-cache (id handler) $))))
      items-cache)))

(defmulti process (fn [_ _ item] (:type item)))

(defmethod process :item [& args]
  (apply process-item args))

(defmethod process :page [& args]
  (apply process-item args))

(defmethod process :feed [ctx items-cache {slugs :items}]
  (u/with-context ctx [config]
    (let [feeds (get-general config :feeds)
          proc  (partial process-item ctx)]
      (->> (map #(assoc {}
                        :type :feed
                        :name %
                        :slugs slugs)
                feeds)
           (reduce proc items-cache)))))

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

(u/defnc generate-notes [config] [items-cache]
  (when-let [notes-dir (u/validate-dir (get-general config :notes-dir))]
    (let [[page-size feed-size]   (get-generals config :notes-per-page :items-per-feed)
          files                   (reverse (u/sorted-files notes-dir markdown-filter))
          slugs                   (map u/slug files)
          proc                    (partial process $ctx)
          pageless                (= page-size 0)]
      (->> (item-seq page-size feed-size slugs)
           (prev-next pageless)
           (reduce proc items-cache)))))

(u/defnc generate-singles [config] [items-cache]
  (let [singles-dir (get-general config :singles-dir)
        files       (u/list-files singles-dir markdown-filter)
        slugs       (map u/slug files)
        proc        (partial process $ctx)]
    (->> (item-seq :single slugs)
         (reduce proc items-cache))))

(defmulti sitemap-item (fn [_ props] (:type props)))
(defmethod sitemap-item :page [_ _] nil)
(defmethod sitemap-item :feed [_ _] nil)
(defmethod sitemap-item :default [config props]
  (let [base-url     (get-general config :base-url)
        transformed  (:transformed props)]
    (assoc transformed
           :loc (str base-url (:slug transformed) html-ext))))
