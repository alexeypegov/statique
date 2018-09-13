(ns statique.noembed
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [clojure.lang Atom]
           [java.io File]))

(def ^:private noembed-url  "http://noembed.com/embed")

(defn noembed
  [url]
  (let [options {:query-params {:url url}}
        {:keys [status headers body error] :as resp} @(http/get noembed-url options)]
    (if error
      nil
      (json/read-str body :key-fn keyword))))