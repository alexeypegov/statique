(ns statique.markdown.renderers
  (:require [clojure.string :as string]
            [statique.markdown.noembed :as noembed])
  (:import
    [org.commonmark.parser Parser Parser$ParserExtension Parser$Builder PostProcessor]
    [org.commonmark.renderer NodeRenderer]
    [org.commonmark.renderer.html
     HtmlRenderer
     HtmlRenderer$HtmlRendererExtension
     HtmlRenderer$Builder
     HtmlNodeRendererFactory
     HtmlNodeRendererContext]
    [org.commonmark.node Node CustomNode Link Text Image CustomNode AbstractVisitor]
    [statique.markdown.media MediaNode]))

(def ^:private media-services ["youtube.com" "youtu.be" "vimeo.com" "flickr.com" "coub.com"])

(defn- url?
  "Checks whatever s is an URL, i.e. starts with URL prefix"
  [s]
  (or
    (string/starts-with? s "http://")
    (string/starts-with? s "https://")))

(defn- process-link-node
  "Checks whatever given node is an URL and replaces it with a MediaNode if needed"
  [node]
  (let [text (.getLiteral node)]
    (when (url? text)
      (let [host (.getHost (java.net.URL. text))]
        (if (some #(string/ends-with? host %) media-services)
          (doto node
            (.insertAfter (MediaNode. text))
            (.unlink))
          (let [link (Link. text nil)]
            (.appendChild link (Text. text))
            (doto node
              (.insertAfter link)
              (.unlink))))))))

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

(defn- image-visitor
  [base-url]
  (proxy [AbstractVisitor] []
    (visit [node]
           (if (instance? Image node)
             (.setDestination node (format "%s%s" base-url (.getDestination node)))
             (proxy-super visitChildren node)))))

(defn- write-media-html
  [node writer]
  (let [url   (.getUrl node)
        data  (noembed/fetch url)
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
  [context]
  (let [writer (.getWriter context)]
    (reify NodeRenderer
      (getNodeTypes [_] #{MediaNode})
      (^void render [_ ^Node node]
             (write-media-html node writer)))))

(def html-node-renderer-factory
  (reify HtmlNodeRendererFactory
    (^NodeRenderer create [_ ^HtmlNodeRendererContext context]
                   (media-node-renderer context))))

(defn- post-processor
  [base-url]
  (reify PostProcessor
    (^Node process [_ ^Node node]
      (.accept node (link-visitor))
      (when base-url
        (.accept node (image-visitor base-url)))
      node)))

; todo get rid of base-url
(defn media-extension
  ([] (media-extension nil))
  ([base-url]
    (reify
      Parser$ParserExtension
      (^void extend
        [_ ^Parser$Builder parserBuilder]
        (.postProcessor parserBuilder (post-processor base-url)))
      HtmlRenderer$HtmlRendererExtension
      (^void extend
        [_ ^HtmlRenderer$Builder rendererBuilder]
        (.nodeRendererFactory rendererBuilder html-node-renderer-factory)))))