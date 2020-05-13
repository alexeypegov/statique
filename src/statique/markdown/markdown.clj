(ns statique.markdown.markdown
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [org.commonmark.parser Parser]
           [org.commonmark.renderer.html HtmlRenderer]
           [org.commonmark.ext.gfm.strikethrough StrikethroughExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterVisitor]))

(def ^:private array-values ["Tags"])

(defonce default-extensions
   [(StrikethroughExtension/create) (YamlFrontMatterExtension/create)])

(defn- string->node [text extensions base-url]
  (-> (doto (Parser/builder) (.extensions extensions))
      (.build)
      (.parse text)))

(defn- node->html
  [node extensions]
  (-> (doto (HtmlRenderer/builder) (.extensions extensions))
      (.build)
      (.render node)))

(defn- map-meta-values
  [m]
  (into {} (for [[k v] m] [(keyword (s/lower-case k)) (if (some #{k} array-values) v (first v))])))

(defn- get-meta
  [node]
  (let [meta-visitor (YamlFrontMatterVisitor.)]
    (.accept node meta-visitor)
    (-> meta-visitor
        (.getData)
        (map-meta-values))))

(defn transform [text & {:keys [extensions base-url] :or {extensions default-extensions}}]
  (let [node (string->node text extensions base-url)]
    (assoc (get-meta node) :body (node->html node extensions))))

(defn transform-file [file & {:keys [extensions base-url] :or {extensions default-extensions}}]
  (-> (slurp file)
      (transform :extensions extensions :base-url base-url)))
