(ns statique.markdown
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [statique.renderers :as renderers])
  (:import [org.commonmark.parser Parser]
           [org.commonmark.renderer.html HtmlRenderer]
           [org.commonmark.ext.gfm.strikethrough StrikethroughExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterVisitor]))

(defrecord Note [title date tags draft html])

(def extensions (list
                  (YamlFrontMatterExtension/create)
                  (StrikethroughExtension/create)
                  renderers/video-extension))

(def array-values ["Tags"])

(defn- prepare-meta
  [front-matter-visitor]
  (let [data (.getData front-matter-visitor)
        mapper (fn [[k v]] {(keyword (s/lower-case k))
                           (cond (some #(= k %) array-values) v
                                 :else (first v))})]
    (merge
      {:draft false}
      (into {} (map mapper data)))))

(defn- make-note
  [front-matter-visitor body]
  (map->Note (assoc (prepare-meta front-matter-visitor) :body body)))

(defn- parse-date
  [date-string date-format]
  (.parse (java.text.SimpleDateFormat. date-format) date-string))

(defn transform
  [s]
  (let [parser (.build (.extensions (Parser/builder) extensions))
        renderer (.build (.extensions (HtmlRenderer/builder) extensions))
        front-matter-visitor (YamlFrontMatterVisitor.)
        node (.parse parser s)
        body (.render renderer node)]
    (do
      (.accept node front-matter-visitor)
      (make-note front-matter-visitor body))))