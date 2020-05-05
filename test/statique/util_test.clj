(ns statique.util-test
  (:require [clojure.java.io :as io]
            [statique.util :as u])
  (:use clojure.test))

(deftest test-paged-seq
  (is (= '() (u/paged-seq 3 '())))
  (is (= '({:index 1, :items (1 2 3), :next? false}) (u/paged-seq 3 '(1 2 3))))
  (is (= '({:index 1, :items (1 2 3), :next? true}
           {:index 2, :items (4 5), :next? false}) (u/paged-seq 3 '(1 2 3 4 5)))))

(deftest test-relative-path
  (is (= "child" (u/relative-path (io/file "/some/root/path") (io/file "/some/root/path/child")))))

(deftest test-assoc?
  (are [result           existing    new] (= result (apply (partial u/assoc? existing) new))
        {:a 1 :b 2}      {}          [:a 1 :b 2]
        {:a 1 :b 2 :c 3} {:a 1 :b 2} [:c 3]
        {:a 1 :b 2}      {:a 1 :b 2} [:c nil]
        {:a 1 :b 2 :c 3} {:a 1 :b 2} [:c 3 :d nil]
        {:a 1 :c '()}    {:a 1}      [:c '()]))
