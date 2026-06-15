(ns statique.static-test
  {:eftest/synchronized true}
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [statique.static :as s]
            [me.raynes.fs :as fs]))

(defn- temp-dir []
  (.toFile (java.nio.file.Files/createTempDirectory
            "statique-test"
            (into-array java.nio.file.attribute.FileAttribute []))))

(defn- write! [dir & path-and-content]
  (let [f (apply io/file dir (butlast path-and-content))]
    (fs/mkdirs (fs/parent f))
    (spit f (last path-and-content))
    f))

(defmacro with-dirs [[src dst] & body]
  `(let [~src (temp-dir)
         ~dst (temp-dir)]
     (try
       ~@body
       (finally
         (fs/delete-dir ~src)
         (fs/delete-dir ~dst)))))

(defn- make-config [dst items]
  {:general {:output-dir (str dst) :copy (mapv str items)}})

(defn- last-modified [f]
  (.lastModified (io/file f)))

(defn- unchanged? [config dst-file]
  (let [t (last-modified dst-file)]
    (Thread/sleep 10)
    (s/copy config)
    (= t (last-modified dst-file))))

(deftest single-file-missing-source
  (with-dirs [src dst]
    (s/copy (make-config dst [(io/file src "missing.txt")]))
    (is (not (fs/exists? (io/file dst "missing.txt"))))))

(deftest single-file-new
  (with-dirs [src dst]
    (write! src "file.txt" "hello")
    (s/copy (make-config dst [(io/file src "file.txt")]))
    (is (= "hello" (slurp (io/file dst "file.txt"))))))

(deftest single-file-unchanged
  (with-dirs [src dst]
    (write! src "file.txt" "hello")
    (write! dst "file.txt" "hello")
    (is (unchanged? (make-config dst [(io/file src "file.txt")]) (io/file dst "file.txt")))))

(deftest single-file-changed
  (with-dirs [src dst]
    (write! src "file.txt" "new")
    (write! dst "file.txt" "old")
    (s/copy (make-config dst [(io/file src "file.txt")]))
    (is (= "new" (slurp (io/file dst "file.txt"))))))

;; directory

(deftest dir-new-file
  (with-dirs [src dst]
    (write! src "imgs" "photo.jpg" "data")
    (s/copy (make-config dst [(io/file src "imgs")]))
    (is (= "data" (slurp (io/file dst "imgs" "photo.jpg"))))))

(deftest dir-unchanged-file
  (with-dirs [src dst]
    (write! src "imgs" "photo.jpg" "data")
    (write! dst "imgs" "photo.jpg" "data")
    (is (unchanged? (make-config dst [(io/file src "imgs")]) (io/file dst "imgs" "photo.jpg")))))

(deftest dir-changed-file
  (with-dirs [src dst]
    (write! src "imgs" "photo.jpg" "new")
    (write! dst "imgs" "photo.jpg" "old")
    (s/copy (make-config dst [(io/file src "imgs")]))
    (is (= "new" (slurp (io/file dst "imgs" "photo.jpg"))))))

(deftest dir-stale-file-removed
  (with-dirs [src dst]
    (write! src "imgs" "keep.jpg" "data")
    (write! dst "imgs" "keep.jpg" "data")
    (write! dst "imgs" "stale.jpg" "old")
    (s/copy (make-config dst [(io/file src "imgs")]))
    (is (not (fs/exists? (io/file dst "imgs" "stale.jpg"))))
    (is (fs/exists? (io/file dst "imgs" "keep.jpg")))))

(deftest dir-no-dst-yet
  (with-dirs [src dst]
    (write! src "imgs" "photo.jpg" "data")
    (s/copy (make-config dst [(io/file src "imgs")]))
    (is (= "data" (slurp (io/file dst "imgs" "photo.jpg"))))))
