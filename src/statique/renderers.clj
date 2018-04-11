(ns statique.renderers
  (:require [clojure.string :as s])
  (:import
    [org.commonmark.parser Parser Parser$ParserExtension Parser$Builder PostProcessor]
    [org.commonmark.renderer NodeRenderer]
    [org.commonmark.renderer.html
     HtmlRenderer
     HtmlRenderer$HtmlRendererExtension
     HtmlRenderer$Builder
     HtmlNodeRendererFactory
     HtmlNodeRendererContext]
    [org.commonmark.node Node CustomNode Link Text CustomNode AbstractVisitor]
    [statique.media MediaNode]))

(def ^:private media-services ["youtube.com" "youtu.be" "vimeo.com" "flickr.com"])

(defn- url?
  "Checks whatever s is an URL, i.e. starts with URL prefix"
  [s]
  (or
    (s/starts-with? s "http://")
    (s/starts-with? s "https://")))

(defn- process-link-node
  "Checks whatever given node is an URL and replaces it with a MediaNode if needed"
  [node]
  (let [text (.getLiteral node)]
    (when (url? text)
      (let [url (java.net.URL. text)
            host (.getHost url)]
        (when (some #(s/ends-with? host %) media-services)
          (doto node
            (.insertAfter (MediaNode. text))
            (.unlink)))))))

(defn- link-visitor
  []
  (let [link-counter (atom 0)]
    (proxy [AbstractVisitor] []
      (visit [node]
             (cond
               (instance? Text node)
                (when (zero? @link-counter)
                  (process-link-node node))
               (instance? Link node)
                (do
                  (swap! link-counter inc)
                  (proxy-super visitChildren node)
                  (swap! link-counter dec))
               :else (proxy-super visitChildren node))))))

(defn- write-media-html
  [node noembed writer]
  (let [url   (.getUrl node)
        data  (noembed url)
        html  (:html data)
        width (:width data)]
    (if (some? width)
      (let [width   (read-string (str width))
            height  (read-string (str (:height data)))
            ratio   (* (float (/ (min width height) (max width height))) 100)]
        (doto writer
          (.tag "div" {"class" "media" "style" (str "padding-top: " ratio "%;")})
          (.raw (or html url))
          (.tag "/div")))
      (doto writer
        (.tag "a" {"href" url})
        (.text url)
        (.tag "/a")))))

(defn- media-node-renderer
  [noembed context]
  (let [writer (.getWriter context)]
    (reify NodeRenderer
      (getNodeTypes [_] #{MediaNode})
      (^void render [_ ^Node node]
             (write-media-html node noembed writer)))))

(defn- html-node-renderer-factory
  [noembed]
  (reify HtmlNodeRendererFactory
    (^NodeRenderer create [_ ^HtmlNodeRendererContext context]
                   (media-node-renderer noembed context))))

(def ^:private link-post-processor
  (reify PostProcessor
    (^Node process [_ ^Node node]
      (.accept node (link-visitor))
      node)))

(defn media-extension
  [noembed]
  (reify
    Parser$ParserExtension
    (^void extend
      [_ ^Parser$Builder parserBuilder]
      (.postProcessor parserBuilder link-post-processor))
    HtmlRenderer$HtmlRendererExtension
    (^void extend
      [_ ^HtmlRenderer$Builder rendererBuilder]
      (.nodeRendererFactory rendererBuilder (html-node-renderer-factory noembed)))))