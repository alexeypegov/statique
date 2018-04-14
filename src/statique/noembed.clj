(ns statique.noembed
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [clojure.lang Atom]
           [java.io File]))

(def ^:private noembed-url "http://noembed.com/embed")

(defn- fetch
  [url]
  (let [options {:query-params {:url url}}
        {:keys [status headers body error] :as resp} @(http/get noembed-url options)]
    (if error
      nil
      (json/read-str body :key-fn keyword))))

(defprotocol PersistentCache
  (data [this url])
  (save [this]))

(deftype Noembed [^Atom cache file]
  PersistentCache
  (data [this url]
        (let [data (or (get @(.cache this) url) (fetch url))]
         (swap! (.cache this) assoc url data)
         data))
  (save [this]
        (.mkdirs (.getParentFile file))
        (println (count @(.cache this)) "noembed entries was written to" (.getPath file))
        (spit file (with-out-str (pr @(.cache this))))))

(defn- read-edn
  [^File file]
  (if (.exists file)
    (read-string (slurp file))
    {}))

(defn make-noembed
  [file-path]
  (let [file  (io/as-file file-path)
        cache (atom (read-edn file))]
    (println (count @cache) "cached noembed entries were read from" (.getPath file))
    (Noembed. cache file)))