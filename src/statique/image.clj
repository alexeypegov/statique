(ns statique.image
  (:require [clojure.java.io :refer [input-stream file]]
            [clojure.string :refer [join]])
  (:import [java.util Arrays]))

(defmulti check (fn [_ b] (type b)))
(defmethod check String [is v]
  (check is (map int v)))
(defmethod check :default [is v]
  (let [size (count v)
        a    (byte-array size)
        b    (byte-array (map unchecked-byte v))]
    (when (and (= size (.read is a))
               (Arrays/equals a b))
      is)))

(defn- read-str
  [is size]
  (let [buf (byte-array size)]
    (when (= size (.read is buf))
      (join (map char buf)))))

(defn- skip
  [is size]
  (when (= size (.skip is size))
    is))

(defn ba->int-be
  [b]
  (let [len       (count b)
        max-shift (* 8 (- len 1))]
    (apply bit-or
           (map (fn [n]
                  (bit-shift-left
                   (bit-and (nth b n) 0xff)
                   (- max-shift (* n 8))))
                (range len)))))

(defn- read-int
  [is parser size]
  (let [a (byte-array size)]
    (when (= size (.read is a))
      (parser a))))

(def ^:private be ba->int-be)
(def ^:private le (comp ba->int-be reverse))

(defn- read-two-ints
  [is parser nob]
  [(read-int is parser nob)
   (read-int is parser nob)])

(defn- process-png
  [is]
  (some-> is
          (check [0x50 0x4e 0x47 0x0d 0x0a 0x1a 0x0a])
          (skip 4)
          (check "IHDR")
          (read-two-ints be 4)))

(defn- next-jpeg-frame-type
  [is]
  (let [frame-size (read-int is be 2)]
    (some-> is
            (skip (- frame-size 2))
            (check [0xff])
            (.read))))

(defn- find-jpeg-frame
  [is types]
  (loop [t (.read is)]
    (cond
      (nil? t)          nil
      (= -1 t)          nil
      (some #{t} types) is
      :else             (recur (next-jpeg-frame-type is)))))

(defn- process-jpeg
  [is]
  (some-> is
    (check [0xd8 0xFF])
    (find-jpeg-frame [0xc0, 0xc1, 0xc2])
    (skip 3)
    (read-two-ints be 2)
    reverse))

(defn- process-gif
  [is]
  (some-> is
    (check "IF8")
    (skip 2)
    (read-two-ints le 2)))

(defmulti read-webp-dimensions (fn [in] (read-str in 4)))
(defmethod read-webp-dimensions "VP8 " [is]
  (when (skip is 10)
    (letfn [(read14bits-le [is] (bit-and (read-int is le 2) 0x3ffff))]
      [(read14bits-le is) (read14bits-le is)])))

; https://developers.google.com/speed/webp/docs/riff_container#extended_file_format
(defmethod read-webp-dimensions "VP8X" [is]
  (when (skip is 8)
    (letfn [(read24bits-le [is] (read-int is le 3))]
      [(+ 1 (read24bits-le is))
       (+ 1 (read24bits-le is))])))

; https://developers.google.com/speed/webp/docs/webp_lossless_bitstream_specification#3_riff_header
(defmethod read-webp-dimensions "VP8L" [is]
  (when (skip is 5)
    (let [two-bytes       (read-int is le 2)
          width           (+ 1 (bit-and two-bytes 0x3fff))
          last-two-bits   (bit-shift-right (bit-and two-bytes 0xc000) 14)
          next-two-bytes  (read-int is le 2)
          height          (+ 1
                             (bit-or
                              (bit-shift-left
                               (bit-and next-two-bytes 0xFFF)
                               2)
                              last-two-bits))]
      [width height])))

(defn- process-webp
  [is]
  (some-> is
    (check "IFF")
    (skip 4)
    (check "WEBP")
    (read-webp-dimensions)))

(defn get-dimensions
  [filepath]
    (with-open [is (input-stream (file filepath))]
      (let [first-byte (.read is)]
        (case first-byte
          0x89 (process-png is)
          0xFF (process-jpeg is)
          0x47 (process-gif is)
          0x52 (process-webp is)
          nil))))

