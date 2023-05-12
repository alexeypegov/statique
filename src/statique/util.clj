(ns statique.util
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [java-time :as time]
            [pandect.algo.crc32 :as crc]
            [clojure.data :as d])
  (:import [java.util Properties]
           [java.io FilenameFilter]
           [java.time.format DateTimeFormatter]
           [java.io FilenameFilter]))

(defmacro dbg
  [x] 
  `(let [x# ~x] (println "dbg:" '~x "=" x#) x#))

(defn working-dir
  []
  (io/file (System/getProperty "user.dir")))

(defn get-version
  [dep]
  (let [path  (str "META-INF/maven/" (or (namespace dep) (name dep)) "/" (name dep) "/pom.properties")]
    (when-let [props (io/resource path)]
      (with-open [stream (io/input-stream props)]
        (-> (doto (Properties.) (.load stream))
            (.getProperty "version"))))))

(defn exit
  "Exits returing a given status and (optionally) prints some message(s)"
  [status & s]
  (when (seq s)
    (apply println s))
  (System/exit status))

(defn list-files
  ; ([] (list-files nil))
  ([dir name-filter] (.listFiles dir name-filter))
  ([dir name-filter comparator]
   (->> (list-files dir name-filter)
        (sort comparator))))

(def name-comparator (fn [a b] (compare (.getName a) (.getName b))))

(defn sorted-files 
  [dir name-filter]
  (list-files dir name-filter name-comparator))

(defn postfix-filter
  "Creates a postfix filename filter for File::listFiles"
  [^String postfix]
  (reify FilenameFilter
    (accept [_ _ name] (str/ends-with? name postfix))))

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
  (when-not (and
             (fs/directory? dir)
             (fs/exists? dir))
    (exit -1 (format "Error: directory was not found (%s)" dir)))
  dir)

(defn prev-next
  "Lazily iterates over collection applying (fn cur, prev, next) to each element (matching predicate) of a collection"
  ([cb col]
   (prev-next any? cb nil col))
  ([pred cb col]
   (prev-next pred cb nil col))
  ([pred cb prev col]
   (lazy-seq
    (when (not-empty col)
      (let [s     (first col)
            rest  (rest col)]
        (if (pred s)
          (let [next (some #(when (pred %) %) rest)]
            (cons
             (cb s prev next)
             (prev-next pred cb s rest)))
          (cons
           s
           (prev-next pred cb prev rest))))))))

(defn parse-local-date
  "Parses local date as start of the day datetime using given format"
  [format tz date]
  {:pre [(string? format) (string? tz) (string? date)]}
  (let [local-date (time/local-date format date)]
    (.atStartOfDay local-date (time/zone-id tz))))

(defn iso-offset
  [datetime]
  (.format DateTimeFormatter/ISO_OFFSET_DATE_TIME datetime))

(defn slug
  [file]
  (str/lower-case (fs/base-name file true)))

(defmulti crc32 type)
(defmethod crc32 java.io.File [file]
  "Returns CRC32 checksum for the given file or 0 if file does not exist"
  (try
    (crc/crc32 file)
    (catch java.io.FileNotFoundException _
      0)))
(defmethod crc32 String [s]
  (crc/crc32 s))

(defn write-file
  [path ^String content & {:keys [data] :or {data false}}]
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