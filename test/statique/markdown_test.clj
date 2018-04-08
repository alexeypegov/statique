(ns statique.markdown-test
  (:require [clojure.test :refer :all]
            [statique.markdown :refer :all])
  (:use [clojure.string :only [join split-lines]]))

(defn long-string [& strings] (clojure.string/join "\n" strings))

(deftest markdown-test
  (testing "markdown parsing"
    (is (= (markdown (long-string "---"
                                  "Title: t1"
                                  "Date: 2018-01-30"
                                  "Tags:"
                                  "  - one"
                                  "  - two"
                                  "---"
                                  "Something [interesting](http://pegov.io)"))
           {:draft false
            :title ["t1"]
            :date ["2018-01-30"]
            :tags ["one" "two"]
            :html "<p>Something <a href=\"http://pegov.io\">interesting</a></p>\n"}))))