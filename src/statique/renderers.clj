(ns statique.renderers
  (:require [statique.noembed :as noembed])
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

; simple regexp to match urls
(def ^:private url-pattern #"^https?://([\w\\.\-]+)(\/?([\w\\.:]+\/?)*)?(\??([\w\-]+(=[\w\-]+)?&?)*)?(#[\S]+)?$")

(def ^:private media-services ["youtube.com" "vimeo.com" "flickr.com" "youtu.be"])

(defn- process-link-node
   [node]
   (let [text (.getLiteral node)]
     (if (re-find url-pattern text)
       (let [url (java.net.URL. text)
             host (.getHost url)]
          (if (some #(clojure.string/ends-with? host %) media-services)
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
                (if (zero? @link-counter)
                  (process-link-node node))
               (instance? Link node)
                (do
                  (swap! link-counter inc)
                  (proxy-super visitChildren node)
                  (swap! link-counter dec))
               :else (proxy-super visitChildren node))))))

(defn- write-media-html
  [node writer]
  (let [url   (.getUrl node)
        data  (noembed/fetch url)
        html  (:html data)
        width (:width data)]
    (if (some? width)
      (let [width   (read-string (str width))
            height  (read-string (str (:height data)))
            ratio   (* 100 (float (/ (min width height) (max width height))))]
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

(def ^:private html-node-renderer-factory
  (reify HtmlNodeRendererFactory
    (^NodeRenderer create [_ ^HtmlNodeRendererContext context]
                   (media-node-renderer context))))

(def ^:private link-post-processor
  (reify PostProcessor
    (^Node process [_ ^Node node]
      (.accept node (link-visitor))
      node)))

(def video-extension
  (reify
    Parser$ParserExtension
    (^void extend
      [_ ^Parser$Builder parserBuilder]
      (.postProcessor parserBuilder link-post-processor))
    HtmlRenderer$HtmlRendererExtension
    (^void extend
      [_ ^HtmlRenderer$Builder rendererBuilder]
      (.nodeRendererFactory rendererBuilder html-node-renderer-factory))))