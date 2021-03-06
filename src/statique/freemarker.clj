(ns statique.freemarker
  (:require [clojure.java.io :as io])
  (:use [clojure.walk :only [postwalk]])
  (:import [freemarker.template Template Configuration DefaultObjectWrapper TemplateExceptionHandler TemplateException]
           [java.io File StringWriter]))

(def ^:private template-extension      "ftl")
(def ^:private def-encoding            "UTF-8")
(def ^:private separator               File/separatorChar)

(defn make-config
  ([template-dir] (make-config template-dir def-encoding))
  ([template-dir encoding]
   (doto (Configuration.)
     (.setObjectWrapper (DefaultObjectWrapper.))
     (.setDirectoryForTemplateLoading template-dir)
     (.setDefaultEncoding encoding)
     (.setTemplateExceptionHandler TemplateExceptionHandler/RETHROW_HANDLER)
     (.setLogTemplateExceptions false)
     (.setWrapUncheckedExceptions true))))

(defn replace-hyphens
  [m]
  (let [stringify (fn [[k v]]
                    (if (keyword? k)
                      [(.replace (name k) "-" "_") v]
                      [k v]))]
    (postwalk #(if (map? %)
                 (into {} (map stringify %))
                 %)
              m)))

(defn render [^Configuration cfg template-name params]
  (let [filename (format "%s.%s" template-name template-extension)
        model    (replace-hyphens params)]
    (try
      (let [template (.getTemplate cfg filename)
            writer   (StringWriter.)]
        (assoc {}
               :status :ok
               :result (do
                         (.process template model writer)
                         (.toString writer))))
      (catch TemplateException ex
        (assoc {}
               :status   :error
               :template filename
               :model    model
               :message  (.getMessage ex))))))
