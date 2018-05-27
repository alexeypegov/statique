(ns statique.util
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-time.format :as timef]
            [clj-time.coerce :as timec]
            [statique.logging :as log])
  (:import java.util.Properties))

(defn long-string
  "Conctenates given strings using newline character"
  [& strings]
  (clojure.string/join \newline strings))

(defn exit
  "Exits returing a given status and (optionally) prints some message"
  [status & s]
  (when (seq? s)
    (apply log/info s))
  (System/exit status))

(defn working-dir
  "Returns current working directory"
  []
  (System/getProperty "user.dir"))

(defn postfix-filter
  "Creates a postfix filename filter for File::listFiles"
  [^String postfix]
  (reify java.io.FilenameFilter
    (accept [this dir name]
            (string/ends-with? name postfix))))

(defn file-comparator
  "Filename based file comparator"
  [file1 file2]
  (compare
    (.getName file1)
    (.getName file2)))

(defn sorted-files
  "Returns sequence of files within the given directory optionally filtered by a postfix"
  [dir & {:keys [postfix]}]
  {:pre [(instance? java.io.File dir) (.isDirectory dir)]}
  (sort
    file-comparator
    (if postfix
      (.listFiles dir (postfix-filter postfix))
      (.listFiles dir))))

(defn local-formatter
  "Creates local date/time formatter"
  [date-format]
  (timef/formatter-local date-format))

(defn parse-date
  "Parses local date/time using given formatter"
  [formatter s]
  (timec/to-date (timef/parse formatter s)))

(defn write-file
  "Writes given content to a filename placed in the given directory
   (will create directories if needed)"
  [dir {:keys [content filename]}]
  {:pre [(string? content)
         (string? filename)]}
  (let [file (io/file dir filename)]
    (do
      (.mkdirs (.getParentFile file))
      (with-open [w (io/writer file)]
        (.write w content)
        (.getPath file)))))

(defn read-edn
  "Reads EDN from the given file, returns default object if empty or no file"
  [path & {:keys [default] :or {default {}}}]
  (let [file (io/as-file path)]
    (if (.exists file)
      (read-string (slurp file))
      default)))

(defprotocol FileCache
  (getCached [this k])
  (save [this]))

(defn make-file-cache
  "Creates a file-based EDN cache given a path and a producer function"
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

(defn get-version
  [dep]
  (let [path (str "META-INF/maven/" (or (namespace dep) (name dep))
                  "/" (name dep) "/pom.properties")
        props (io/resource path)]
    (when props
      (with-open [stream (io/input-stream props)]
        (let [props (doto (Properties.) (.load stream))]
          (.getProperty props "version"))))))