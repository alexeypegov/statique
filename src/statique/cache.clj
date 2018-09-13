(ns statique.cache
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [statique.logging :as log]))

(defprotocol Cache
  (read-v [this k] [this k d])
  (write-v [this k v]))

(defprotocol FileCache
  (save [this]))

(defn make-file-cache
  [file & {:keys [producer-fn] :or {producer-fn nil}}]
  (let [c-file  (io/as-file file)
        r-data  (u/read-edn file)
        w-data  (atom {})]
    (reify
      Cache
      (read-v [this k] (.read-v this k nil))
      (read-v [this k d]
              (if producer-fn
                (let [v (or (get r-data k) (producer-fn k))]
                  (.write-w this k v)
                  v)
                (get r-data k)))
      (write-v [_ k v]
               (swap! w-data assoc k v))
      FileCache
      (save [_]
            (.mkdirs (.getParentFile c-file))
            (spit c-file (with-out-str (pr @w-data)))
            (log/debug (count @w-data) "entries were cached to" (.getName c-file))))))