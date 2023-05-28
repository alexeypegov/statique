(ns statique.util
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [pandect.algo.crc32 :as crc])
  (:import [java.util Properties]
           [java.io FilenameFilter]))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
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
  "Lazily iterates over collection applying (fn cur, prev, next) to each element"
  ([cb col] (prev-next any? cb col))
  ([pred cb col] (prev-next pred cb nil col col))
  ([pred cb prev col rest-col]
   (lazy-seq
    (when (not-empty rest-col)
      (let [cur  (first rest-col)
            rest (rest rest-col)]
        (if (pred cur)
          (letfn [(pred-fn [col] (some #(when (pred %) %) col))]
            (let [prev' (or prev (pred-fn (reverse col)))
                  next (or (pred-fn rest) (pred-fn col))]
              (cons
               (cb cur prev' next)
               (prev-next pred cb cur col rest))))
          (cons
           cur
           (prev-next pred cb prev col rest))))))))

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

(defmulti check-render-error :status)
(defmethod check-render-error :ok [{:keys [result]}] result)
(defmethod check-render-error :error [{:keys [message]}] (exit -1 message))