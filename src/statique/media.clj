(ns statique.media
  (:import [org.commonmark.node CustomNode])
  (:gen-class :extends org.commonmark.node.CustomNode
              :name statique.media.MediaNode
              :main false
              :init init
              :state state
              :constructors {[String] []}
              :methods [[getUrl [] String]]))

(defn -init
  [^String url]
  [[] (atom {:url url})])

(defn -getUrl
  [this]
  (:url @(.state this)))