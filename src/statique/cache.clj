(ns statique.cache
  (:require [clojure.java.io :as io]
            [statique.util :as util]
            [statique.logging :as log])
  (:refer-clojure :rename {get map-get})
  (:import [java.io File]))

(defprotocol Cache
  (get [this k] [this k d])
  (put [this k v]))

(defprotocol FileCache
  "Is made compatible with Closeable to let use with 'with-open'"
  (close [this]))

(defn file-cache
  ([file & {:keys [instant] :or {instant false}}]
   (let [^File c-file (io/as-file file)
         edn (util/read-edn file)
         r-data edn
         w-data (atom (if instant edn {}))]
     (log/debug (count r-data) "entries were read from" file)
     (reify
       Cache
       (get [this k]
         (.get this k nil))
       (get [this k d]
         (if instant
           (map-get @w-data k d)
           (map-get r-data k d)))
       (put [_ k v]
         (swap! w-data assoc k v))
       FileCache
       (close [_]
         (.mkdirs (.getParentFile c-file))
         (spit c-file (with-out-str (pr @w-data)))
         (log/debug (count @w-data) "entries were cached to" file))))))
