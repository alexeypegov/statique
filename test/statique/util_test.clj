(ns statique.util-test
  (:require [clojure.java.io :as io]
            [statique.util :as u]
            [clojure.test :refer [deftest is are]]))

(deftest file-cache
  (with-redefs [u/read-edn (constantly {"b" 100})]
    (let [cache (u/file-cache (io/file "non-existing.edn") (fn [k] (when-not (= :nothing k) (apply str (repeat 3 k)))))]
      (are [result key] (= result (cache key))
        {"b" 100}   :all
        "aaa"       "a"
        100         "b"
        nil         :nothing
        {"a" "aaa"
         "b" 100}   :all))))

(deftest prev-next
  (is (= '([0 0 0]) (u/prev-next (fn [a b c] [a b c]) (range 1))))
  (is (= '([0 4 1][1 0 2][2 1 3][3 2 4][4 3 0]) (u/prev-next (fn [a b c] [a b c]) (range 5)))))
  (is (= '([0 4 2] 1 [2 0 4] 3 [4 2 0]) (u/prev-next even? (fn [a b c] [a b c]) (range 5))))
