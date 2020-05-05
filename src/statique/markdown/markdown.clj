(ns statique.markdown.markdown
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [statique.markdown.renderers :as r])
  (:import [org.commonmark.parser Parser]
           [org.commonmark.renderer.html HtmlRenderer]
           [org.commonmark.ext.gfm.strikethrough StrikethroughExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterVisitor]))

(def ^:private array-values ["Tags"])

(defn- string->node
  ([s] (string->node s nil))
  ([s base-url]
   (let [extensions [(StrikethroughExtension/create) (YamlFrontMatterExtension/create) (r/media-extension base-url)]]
     (-> (doto (Parser/builder) (.extensions extensions))
         .build
         (.parse s)))))

(defn- node->html
  [node]
  (-> (doto (HtmlRenderer/builder) (.extensions [(StrikethroughExtension/create) (r/media-extension)]))
      .build
      (.render node)))

(defn- map-meta-values
  [m]
  (into {} (for [[k v] m] [(keyword (s/lower-case k)) (if (some #{k} array-values) v (first v))])))

(defn- get-meta
  [node]
  (let [meta-visitor (YamlFrontMatterVisitor.)]
    (.accept node meta-visitor)
    (-> (.getData meta-visitor)
        map-meta-values)))

(defn transform
  ([s] (transform nil s))
  ([base-url s]
   (let [node (string->node s base-url)]
     (merge {:body (node->html node)} (get-meta node)))))

(defn transform-file
  ([file] (transform-file nil file))
  ([base-url file]
   (->> (slurp file)
        (transform base-url))))
