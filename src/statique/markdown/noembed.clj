(ns statique.markdown.noembed
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [clojure.lang Atom]
           [java.io File]))

(def ^:private noembed-url "http://noembed.com/embed")

(defn fetch
  [url]
  (let [options              {:query-params {:url url}}
        {:keys [body error]} @(http/get noembed-url options)]
    (when-not error
      (try
        (json/read-str body :key-fn keyword)
        (catch Throwable e
          nil)))))