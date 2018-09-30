(ns statique.freemarker
  (:require [clojure.java.io :as io])
  (:use [clojure.walk :only [postwalk]])
  (:import [freemarker.template Configuration DefaultObjectWrapper]
           [java.io File StringWriter]))

(def ^:private def-ext      ".ftl")
(def ^:private def-encoding "UTF-8")
(def ^:private separator    File/separatorChar)

(defn make-config
  ([template-dir] (make-config template-dir def-encoding))
  ([template-dir encoding]
    (doto (Configuration.)
                (.setObjectWrapper (DefaultObjectWrapper.))
                (.setDirectoryForTemplateLoading template-dir)
                (.setDefaultEncoding encoding))))

(defn m->model
  [m]
  (let [stringify (fn [[k v]] (if (keyword? k) [(.replace (name k) "-" "_") v] [k v]))]
    (postwalk #(cond (map? %) (into {} (map stringify %))
                     :else %)
              m)))

(defn render
  [^Configuration cfg template-name params]
  (let [writer    (StringWriter.)
        name      (str template-name def-ext)
        template  (.getTemplate cfg name)
        model     (m->model params)]
    (.process template model writer)
    (.toString writer)))