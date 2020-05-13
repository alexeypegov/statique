(ns statique.util
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [me.raynes.fs :as fs]
            [java-time :as time]
            [pandect.algo.crc32 :as crc])
  (:import [java.util Properties]
           [java.io File FilenameFilter]
           [java.time.format DateTimeFormatter]
           [java.io File FilenameFilter]
           [java.time LocalDate]))

(defn working-dir []
  (io/file (System/getProperty "user.dir")))

(defn get-version [dep]
  (let [path  (str "META-INF/maven/" (or (namespace dep) (name dep)) "/" (name dep) "/pom.properties")]
    (when-let [props (io/resource path)]
      (with-open [stream (io/input-stream props)]
        (-> (doto (Properties.) (.load stream))
            (.getProperty "version"))))))

(defn exit
  "Exits returing a given status and (optionally) prints some message"
  [status & s]
  (when (seq s)
    (apply println s))
  (System/exit status))

(defn list-files
  ([dir] (list-files nil))
  ([dir name-filter] (.listFiles dir name-filter))
  ([dir name-filter comparator]
   (->> (list-files dir name-filter)
        (sort comparator))))

(def name-comparator (fn [a b] (compare (.getName a) (.getName b))))

(defn sorted-files [dir name-filter]
  (list-files dir name-filter name-comparator))

(defn postfix-filter
  "Creates a postfix filename filter for File::listFiles"
  [^String postfix]
  (reify FilenameFilter
    (accept [_ _ name] (s/ends-with? name postfix))))

(defn relative-path [root file]
  (-> (.toPath root)
      (.relativize (.toPath file))
      (.toString)))

(defn read-edn
  "Reads EDN from the given file, returns default object if empty or no file"
  ([path] (read-edn path {}))
  ([path default]
   (let [file (io/as-file path)]
     (if (.exists file)
       (read-string (slurp file))
       default))))

(defn validate-dir
  "Checks whatever given file is directory and it is exist"
  [dir]
  (when-not (and (fs/directory? dir) (fs/exists? dir))
    (exit -1 (format "Error: directory was not found (%s)" dir)))
  dir)

(defn paged-seq
  ([page-size col]
   (paged-seq page-size col 1))
  ([page-size col index]
   {:pre [(number? page-size) (seq? col)]}
   (lazy-seq
    (when (not-empty col)
      (let [items (take page-size col)
            rest  (drop page-size col)]
        (cons
         {:index index
          :items items
          :next? (not (empty? rest))}
         (paged-seq page-size rest (inc index))))))))

(defn assoc?
  "Associates all non-empty values"
  [m & pairs]
  {:pre (even? (count pairs))}
  (as-> pairs $
    (partition 2 $)
    (filter second $)
    (apply concat $)
    (if (seq $)
      (apply assoc m $)
      m)))

(defn parse-local-date
  "Parses local date/time using given formatter"
  [format tz date]
  {:pre [(string? format) (string? tz) (string? date)]}
  (let [local-date (time/local-date format date)]
    (.atStartOfDay local-date (time/zone-id tz))))

(defn rfc-822 [datetime]
  (.format DateTimeFormatter/RFC_1123_DATE_TIME datetime))

(defn rfc-3339 [datetime]
  (.format DateTimeFormatter/ISO_OFFSET_DATE_TIME datetime))

(defn slug [file]
  (s/lower-case (fs/base-name file true)))

(defn crc32 [file]
  (when (fs/exists? file) (crc/crc32 file)))

(defn write-file [path ^String content & {:keys [data] :or {data false}}]
  (let [file (io/file path)]
    (fs/mkdirs (fs/parent file))
    (if data
      (->> (pr content)
           (with-out-str)
           (spit file))
      (spit file content :encoding "UTF-8"))))

(defn file-cache
  "Returns a caching function that calculates a value using new-fn or returns previously calculated one if any,
   returns the whole cache if ':all' is passed as a key, nils are not cached!"
  [file new-fn]
  (let [m (atom (read-edn file))]
    (fn [key]
      {:pre [(or (keyword? key) (string? key)) (fn? new-fn)]}
      (if (= :all key)
        @m
        (if-let [existing (get @m key)]
          existing
          (when-let [new (new-fn key)]
            (swap! m assoc key new)
            new))))))
