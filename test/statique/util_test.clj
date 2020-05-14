(ns statique.util-test
  (:require [clojure.java.io :as io]
            [statique.util :as u])
  (:use clojure.test))

(deftest paged-seq
  (is (= '() (u/paged-seq 3 '())))
  (is (= '({:index 1, :items (1 2 3), :next? false}) (u/paged-seq 3 '(1 2 3))))
  (is (= '({:index 1, :items (1 2 3), :next? true}
           {:index 2, :items (4 5), :next? false}) (u/paged-seq 3 '(1 2 3 4 5)))))

(deftest relative-path
  (is (= "child" (u/relative-path (io/file "/some/root/path") (io/file "/some/root/path/child")))))

(deftest assoc?
  (are [result           existing    new] (= result (apply (partial u/assoc? existing) new))
    {:a 1 :b 2}          {}          [:a 1 :b 2]
    {:a 1 :b 2 :c 3}     {:a 1 :b 2} [:c 3]
    {:a 1 :b 2 :c false} {:a 1 :b 2} [:c false]
    {:a 1 :b 2}          {:a 1 :b 2} [:c nil]
    {:a 1 :b 2 :c 3}     {:a 1 :b 2} [:c 3 :d nil]
    {:a 1 :c '()}        {:a 1}      [:c '()]))

(deftest file-cache
  (with-redefs [u/read-edn (constantly {"b" 100})]
    (let [cache (u/file-cache (io/file "non-existing.edn") (fn [k] (if-not (= :nothing k) (apply str (repeat 3 k)))))]
      (are [result key] (= result (cache key))
        {"b" 100}   :all
        "aaa"       "a"
        100         "b"
        nil         :nothing
        {"a" "aaa"
         "b" 100}   :all))))
