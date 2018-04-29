(ns statique.noembed
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [statique.logging :as log]
            [statique.util :as u])
  (:import [clojure.lang Atom]
           [java.io File]))

(def ^:private noembed-url  "http://noembed.com/embed")
(def ^:private cache-name   "noembed.edn")

(defn- fetch
  [url]
  (let [options {:query-params {:url url}}
        {:keys [status headers body error] :as resp} @(http/get noembed-url options)]
    (if error
      nil
      (json/read-str body :key-fn keyword))))

(defn make-noembed
  [cache-dir]
  (let [file  (io/file cache-dir cache-name)]
    (u/make-file-cache file fetch)))