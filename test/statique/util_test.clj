(ns statique.util-test
  (:require [clojure.test :refer :all]
            [statique.util :refer :all]))

(deftest paged-seq-test
  (testing "paged-sequence"
    (is (= [(make-page 1 (range 5) false)] (paged-seq 10 (range 5))))
    (is (= [(make-page 1 (range 10) true)
            (make-page 2 (range 10 20) true)
            (make-page 3 (range 20 23) false)] (paged-seq 10 (range 23))))))