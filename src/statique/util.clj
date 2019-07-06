(ns statique.util
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [java-time :as time]
            [statique.logging :as log])
  (:import [java.util Properties]
           [java.time.format DateTimeFormatter]
           [java.io File FilenameFilter]
           [java.nio.file Files]
           [java.time LocalDate]))

(defn working-dir
  []
  (System/getProperty "user.dir"))

(defn long-string
  "Conctenates given strings using newline character"
  [& strings]
  (str/join \newline strings))

(defn exit
  "Exits returing a given status and (optionally) prints some message"
  [status & s]
  (when (seq? s)
    (apply log/info s))
  (System/exit status))

(defn postfix-filter
  "Creates a postfix filename filter for File::listFiles"
  [^String postfix]
  (reify FilenameFilter
    (accept [_ _ name]
            (str/ends-with? name postfix))))

(defn file-comparator
  "Filename based file comparator"
  [file1 file2]
  (compare
    (.getName file1)
    (.getName file2)))

(defn sorted-files
  "Returns sorted sequence of files within the given directory optionally filtered by a postfix"
  ([^File dir] (sorted-files dir nil))
  ([dir postfix]
    {:pre [(instance? File dir) (.isDirectory dir)]}
    (sort
      file-comparator
      (if postfix
        (.listFiles dir (postfix-filter postfix))
        (.listFiles dir)))))

(defn parse-local-date
  "Parses local date/time using given formatter"
  [format tz date]
  (let [^LocalDate local-date (time/local-date format date)]
    (.atStartOfDay local-date (time/zone-id tz))))

(defn rfc-822
  [datetime]
  (.format DateTimeFormatter/RFC_1123_DATE_TIME datetime))

(defn rfc-3339
  [datetime]
  (.format DateTimeFormatter/ISO_OFFSET_DATE_TIME datetime))

(defn write-file
  [path ^String content]
  (let [file (io/file path)]
    (.mkdirs (.getParentFile file))
    (with-open [w (io/writer file)]
      (.write w content)
      (.getPath file))))

(defn read-edn
  "Reads EDN from the given file, returns default object if empty or no file"
  ([path] (read-edn path {}))
  ([path default]
    (let [file (io/as-file path)]
      (if (.exists file)
        (read-string (slurp file))
        default))))

(defn get-version
  [dep]
  (let [path  (str "META-INF/maven/" (or (namespace dep) (name dep)) "/" (name dep) "/pom.properties")]
    (when-let [props (io/resource path)]
      (with-open [stream (io/input-stream props)]
        (let [props (doto (Properties.) (.load stream))]
          (.getProperty props "version"))))))

(defn relative-path
  [root file]
  (.toString (.relativize
               (.toPath (io/as-file root))
               (.toPath file))))

(defn paged-seq
  ([page-size col]
    (paged-seq page-size col 1))
  ([page-size col index]
   {:pre (seq? col)}
   (lazy-seq
     (when (not-empty col)
       (let [items (take page-size col)
             rest  (drop page-size col)]
         (cons
           {:index index
            :items items
            :next? (not (empty? rest))}
           (paged-seq page-size rest (inc index))))))))