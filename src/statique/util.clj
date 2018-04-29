(ns statique.util
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-time.format :as timef]
            [clj-time.coerce :as timec]
            [statique.logging :as log]))

(defn ext-filter
  [ext]
  (reify java.io.FilenameFilter
    (accept [this dir name]
            (string/ends-with? name ext))))

(defn file-comparator
  [file1 file2]
  (compare (.getName file1) (.getName file2)))

(defn sorted-files
  [dir & {:keys [extension] :or {extension "md"}}]
  (sort file-comparator (.listFiles dir (ext-filter extension))))

(defn file-name
  [file]
  (let [name (.getName file)]
    (subs name 0 (string/last-index-of name "."))))

(defn local-formatter
  [date-format]
  (timef/formatter-local date-format))

(defn parse-date
  "Parses local date"
  [formatter s]
  (timec/to-date (timef/parse formatter s)))

(defn write-file
  [output-dir {:keys [content filename]}]
  {:pre [(string? content) (string? filename)]}
  (let [file (io/file output-dir filename)]
    (do
      (.mkdirs (.getParentFile file))
      (with-open [w (io/writer file)]
        (.write w content)
        (.getPath file)))))

(defn read-edn
  [path]
  (let [file (io/as-file path)]
    (if (.exists file)
      (read-string (slurp file))
      {})))

(defprotocol FileCache
  (getCached [this k])
  (save [this]))

(defn make-file-cache
  [path producer]
  (let [file        (io/as-file path)
        data        (read-edn file)
        write-cache (atom {})]
    (log/debug (count data) "entries were read from" (.getPath file))
    (reify FileCache
      (getCached [_ k]
           (let [v (or (get data k) (producer k))]
            (swap! write-cache assoc k v)
            v))
      (save [_]
            (.mkdirs (.getParentFile file))
            (spit file (with-out-str (pr @write-cache)))
            (log/debug (count @write-cache) "entries were cached to" (.getPath file))))))