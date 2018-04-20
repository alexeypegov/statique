(ns statique.util
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-time.format :as timef]
            [clj-time.coerce :as timec]))

(defn ext-filter
  [ext]
  (reify java.io.FilenameFilter
    (accept [this dir name]
            (string/ends-with? name ext))))

(defn file-comparator
  [file1 file2]
  (compare (.getName file1) (.getName file2)))

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