(ns statique.cache
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.logging :as log])
  (:refer-clojure :rename {get map-get}))

(defprotocol Cache
  (get [this k] [this k d])
  (put [this k v]))

(defprotocol FileCache
  (close [this]))

(defn make-file-cache
  [file & {:keys [producer-fn] :or {producer-fn nil}}]
  (let [c-file  (io/as-file file)
        r-data  (u/read-edn file)
        w-data  (atom {})]
    (log/debug (count r-data) "entries were read from" file)
    (reify
      Cache
      (get [this k]
           (.get this k nil))
      (get [this k d]
              (if producer-fn
                (let [cache-value (map-get r-data k :not-found)
                      result      (if (= cache-value :not-found) (producer-fn k) cache-value)]
                  (.put this k result)
                  result)
                (map-get r-data k)))
      (put [_ k v]
               (swap! w-data assoc k v))
      FileCache
      (close [_]
            (.mkdirs (.getParentFile c-file))
            (spit c-file (with-out-str (pr @w-data)))
            (log/debug (count @w-data) "entries were cached to" file)))))