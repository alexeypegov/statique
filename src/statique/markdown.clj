(ns statique.markdown
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [statique.noembed :as noembed]
            [statique.renderers :as renderers])
  (:import [org.commonmark.parser Parser]
           [org.commonmark.renderer.html HtmlRenderer]
           [org.commonmark.ext.gfm.strikethrough StrikethroughExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterExtension]
           [org.commonmark.ext.front.matter YamlFrontMatterVisitor]))

(defrecord Note [title date tags draft html])

(def ^:private noembed-cache (atom {}))
(def ^:private noembed-file "noembed.edn")
(def ^:private array-values ["Tags"])

(defn- make-extensions
  [cached-noembed]
  (list
    (YamlFrontMatterExtension/create)
    (StrikethroughExtension/create)
    (renderers/media-extension cached-noembed)))

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

(defn- read-edn
  [file]
  (if (.exists (io/as-file file))
    (read-string (slurp file))
    {}))

(defn read-noembed-cache
  [dir]
  (let [file (io/file dir noembed-file)]
    (reset! noembed-cache (read-edn file))
    (println (count @noembed-cache) "cached noembed entries were read from" (.getPath file))))

(defn write-noembed-cache
  [dir]
  (.mkdirs dir)
  (let [file (io/file dir noembed-file)]
    (println (count @noembed-cache) "noembed entries was written to" (.getPath file))
    (spit (io/file dir noembed-file) (with-out-str (pr @noembed-cache)))))

(defn- cached-noembed
  []
  (fn [url]
    (let [data (or (get @noembed-cache url) (noembed/fetch url))]
      (swap! noembed-cache assoc url data))))

(defn transform
  [s]
  (let [extensions            (make-extensions (cached-noembed))
        parser                (.build (.extensions (Parser/builder) extensions))
        renderer              (.build (.extensions (HtmlRenderer/builder) extensions))
        front-matter-visitor  (YamlFrontMatterVisitor.)
        node                  (.parse parser s)
        body                  (.render renderer node)]
    (do
      (.accept node front-matter-visitor)
      (make-note front-matter-visitor body))))