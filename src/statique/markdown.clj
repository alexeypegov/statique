(ns statique.markdown
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [org.commonmark.parser Parser]
           [org.commonmark.renderer.html HtmlRenderer]
           [org.commonmark.ext.gfm.strikethrough StrikethroughExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterVisitor]))

(def ^:private array-values ["Tags"])
(def ^:private default-extensions [(YamlFrontMatterExtension/create) (StrikethroughExtension/create)])

(defn- prepare-meta
  [front-matter-visitor]
  (let [data    (.getData front-matter-visitor)
        mapper  (fn [[k v]] {(keyword (s/lower-case k))
                           (cond (some #(= k %) array-values) v
                                 :else (first v))})]
    (merge
      {:draft false}
      (into {} (map mapper data)))))

(defn- make-note
  [front-matter-visitor body]
  (assoc (prepare-meta front-matter-visitor) :body body))

;(defn- parse-date
;  [date-string date-format]
;  (.parse (java.text.SimpleDateFormat. date-format) date-string))

(defn transform
  [s & {:keys [extensions] :or {extensions []}}]
  (let [es                    (flatten (concat default-extensions (list extensions)))
        parser                (.build (.extensions (Parser/builder) es))
        renderer              (.build (.extensions (HtmlRenderer/builder) es))
        front-matter-visitor  (YamlFrontMatterVisitor.)
        node                  (.parse parser s)
        body                  (.render renderer node)]
    (do
      (.accept node front-matter-visitor)
      (make-note front-matter-visitor body))))