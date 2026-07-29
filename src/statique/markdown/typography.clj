(ns statique.markdown.typography
  (:require [clojure.string :as s])
  (:import
   [org.commonmark.parser Parser$ParserExtension Parser$Builder PostProcessor]
   [org.commonmark.node Node Text AbstractVisitor]))

(defn- replace-dashes
  [text]
  (-> text
      (s/replace #"---" "—")
      (s/replace #"--" "–")))

(defn- dash-visitor
  []
  (proxy [AbstractVisitor] []
    (visit [node]
      (if (instance? Text node)
        (.setLiteral node (replace-dashes (.getLiteral node)))
        (proxy-super visitChildren node)))))

(defn- post-processor
  []
  (reify PostProcessor
    (^Node process [_ ^Node node]
      (.accept node (dash-visitor))
      node)))

(defn dashes-extension
  []
  (reify Parser$ParserExtension
    (^void extend
      [_ ^Parser$Builder parserBuilder]
      (.postProcessor parserBuilder (post-processor)))))
