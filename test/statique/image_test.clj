(ns statique.image-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [statique.image :as image]))

(defn- ascii
  [s]
  (map int s))

(defn- u32
  [n]
  [(bit-and (bit-shift-right n 24) 0xff)
   (bit-and (bit-shift-right n 16) 0xff)
   (bit-and (bit-shift-right n 8) 0xff)
   (bit-and n 0xff)])

(defn- box
  [type payload]
  (let [payload (vec payload)]
    (concat (u32 (+ 8 (count payload)))
            (ascii type)
            payload)))

(defn- avif-bytes
  [width height]
  (let [ispe (box "ispe" (concat [0 0 0 0] (u32 width) (u32 height)))
        ipco (box "ipco" ispe)
        iprp (box "iprp" ipco)
        meta (box "meta" (concat [0 0 0 0] iprp))
        ftyp (box "ftyp" (concat (ascii "avif") [0 0 0 0] (ascii "avif")))]
    (byte-array (map unchecked-byte (concat ftyp meta)))))

(defn- write-temp-image
  [suffix bytes]
  (let [file (java.io.File/createTempFile "statique-image-" suffix)]
    (.deleteOnExit file)
    (with-open [out (io/output-stream file)]
      (.write out bytes))
    file))

(deftest avif-dimensions-test
  (testing "reads AVIF dimensions from image spatial extents"
    (let [file (write-temp-image ".avif" (avif-bytes 640 480))]
      (is (= [640 480] (image/get-dimensions (.getPath file)))))))
