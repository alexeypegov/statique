(ns statique.markdown-test
  (:require [clojure.test :refer :all]
            [statique.markdown :refer :all]
            [statique.util :as u]))

(deftest markdown-test
  (testing "markdown parsing"
    (is (= {:draft false
            :title "t1"
            :date "2018-01-30"
            :tags ["one" "two"]
            :body "<p>Something <a href=\"http://pegov.io\">interesting</a></p>\n"}
           (transform (u/long-string "---"
                                  "Title: t1"
                                  "Date: 2018-01-30"
                                  "Tags:"
                                  "  - one"
                                  "  - two"
                                  "---"
                                  "Something [interesting](http://pegov.io)"))))))