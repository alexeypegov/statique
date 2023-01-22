(ns statique.markdown.renderers
  (:require [clojure.string :as string])
  (:import
    [java.util Map]
    [org.commonmark.parser Parser$ParserExtension Parser$Builder PostProcessor]
    [org.commonmark.renderer NodeRenderer]
    [org.commonmark.renderer.html
     HtmlRenderer$HtmlRendererExtension
     HtmlRenderer$Builder
     HtmlNodeRendererFactory
     HtmlNodeRendererContext
     AttributeProviderFactory
     AttributeProviderContext
     AttributeProvider]
    [org.commonmark.node Node Link Text Image AbstractVisitor]
    [statique.markdown.media MediaNode]))

(def ^:private media-services ["youtube.com" "youtu.be" "vimeo.com" "flickr.com" "coub.com" "twitter.com" "soundcloud.com"])

(defn- url? 
  [s]
  (or
   (string/starts-with? s "http://")
   (string/starts-with? s "https://")))

(defn- get-host 
  [text]
  (when (url? text)
    (.getHost (java.net.URL. text))))

(defn- process-link-node 
  [node]
  (let [text (.getLiteral node)]
    (when-let [host (get-host text)]
      (if (some #(string/ends-with? host %) media-services)
        (doto node
          (.insertAfter (MediaNode. text))
          (.unlink))
        (let [link (Link. text nil)]
          (.appendChild link (Text. text))
          (doto node
            (.insertAfter link)
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

(defn- add-baseurl-image-visitor 
  [base-url]
  (proxy [AbstractVisitor] []
    (visit [node]
      (if (instance? Image node)
        (.setDestination node (format "%s%s" base-url (.getDestination node)))
        (proxy-super visitChildren node)))))

(defmulti ^:private typed-media (fn [{:keys [error type]} _] (if error
                                                                     :error
                                                                     (case     type
                                                                       "photo" :photo
                                                                       "video" :rich
                                                                       "rich"  :rich
                                                                               :unknown))))
(defmethod typed-media :error 
  [{:keys [error url]} writer]
  (doto writer
    (.tag "a" {"href" url})
    (.text error)
    (.tag "/a")))
(defmethod typed-media :photo 
  [{:keys [media_url]} writer]
  (doto writer
    (.tag "img" {"src" media_url "loading" "lazy"})))
(defmethod typed-media :rich 
  [{:keys [thumbnail_url thumbnail_width thumbnail_height url title html provider_name]} writer]
  (if true ; disable thumbnails
    (doto writer
      (.tag "div" {"class" "media" "data-rovider" provider_name})
      (.raw html)
      (.tag "/div"))
    (doto writer
      (.tag "a" {"href" url "data-provider" provider_name})
      (.tag "img" {"src" thumbnail_url "width" (str thumbnail_width) "height" (str thumbnail_height) "title" title "loading" "lazy"})
      (.tag "/a"))))
(defmethod typed-media :unknown 
  [{:keys [url title]} writer]
  (doto writer
    (.tag "a" {"href" url})
    (.text title)
    (.tag "/a")))

(defn- write-media-html 
  [node writer noembed]
  (let [url (.getUrl node)]
    (when-let [data (noembed url)]
      (typed-media data writer))))

(defn- media-node-renderer 
  [context noembed]
  (let [writer (.getWriter context)]
    (reify NodeRenderer
      (getNodeTypes [_] #{MediaNode})
      (^void render [_ ^Node node]
        (write-media-html node writer noembed)))))

; disabled
#_(defn- skip-parent-para-renderer 
  [context]
  (proxy [CoreHtmlNodeRenderer] [^HtmlNodeRendererContext context]
     (getNodeTypes [] #{Paragraph})
     (^void render [^Node node]
       (if (instance? Document (.getParent node))
         (proxy-super visitChildren node)
         (proxy-super visit node)))))

(defn- image-attr-provider
  []
  (reify AttributeProvider
    (^void setAttributes [_ ^Node node ^String _ ^Map attributes]
           (when (instance? Image node)
             (.put attributes "loading" "lazy")
             (when (string/includes? (.getDestination node) "@2x")
               (let [destination (.getDestination node)]
                 (.put attributes "src" (string/replace destination #"@2x" ""))
                 (.put attributes "srcset" (str destination " 2x"))))))))

(defn- html-node-renderer-factory 
  [fetcher]
  (reify HtmlNodeRendererFactory
    (^NodeRenderer create [_ ^HtmlNodeRendererContext context]
      (media-node-renderer context fetcher))))

#_(defn- skip-parent-para-renderer-factory 
  []
  (reify HtmlNodeRendererFactory
    (^NodeRenderer create [_ ^HtmlNodeRendererContext context]
      (skip-parent-para-renderer context))))

(defn- attr-provider-factory
  []
  (reify AttributeProviderFactory
    (^AttributeProvider create [_ ^AttributeProviderContext _]
                        (image-attr-provider))))

(defn- post-processor 
  [base-url]
  (reify PostProcessor
    (^Node process [_ ^Node node]
      (.accept node (link-visitor))
      (when base-url
        (.accept node (add-baseurl-image-visitor base-url)))
      node)))

(defn media-extension
  ([noembed] (media-extension noembed nil))
  ([noembed base-url]
   (reify
     Parser$ParserExtension
     (^void extend
       [_ ^Parser$Builder parserBuilder]
       (.postProcessor parserBuilder (post-processor base-url)))
     HtmlRenderer$HtmlRendererExtension
     (^void extend
       [_ ^HtmlRenderer$Builder rendererBuilder]
       (.nodeRendererFactory rendererBuilder (html-node-renderer-factory noembed))
       #_(.nodeRendererFactory rendererBuilder (skip-parent-para-renderer-factory))
       (.attributeProviderFactory rendererBuilder (attr-provider-factory))))))
