(ns statique.markdown.noembed
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(def ^:private noembed-url "http://noembed.com/embed")

(defn fetch 
  "Fetches noembed data for the given media URL"
  [url]
  (let [options              {:query-params {:url url :timeout 1000}}
        {:keys [body error]} @(http/get noembed-url options)]
    (if-not error
      (try
        (json/read-str body :key-fn keyword)
        (catch Throwable e
          (log/warn url (.getMessage e))
          {:error (.getMessage e) 
           :url   url}))
      (do 
        (log/warn url)
        {:error url
         :url   url}))))