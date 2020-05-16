(ns statique.markdown.noembed
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(def ^:private noembed-url "http://noembed.com/embed")

(defn fetch [url]
  (let [options              {:query-params {:url url}}
        {:keys [body error]} @(http/get noembed-url options)]
    (if-not error
      (try
        (let [{:keys [error html] :as data} (json/read-str body :key-fn keyword)]
          (if error
            data
            {:html html}))
        (catch Throwable e
          (log/warn url (.getMessage e))))
      (log/warn url error))))
