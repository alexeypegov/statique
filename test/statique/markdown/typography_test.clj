(ns statique.markdown.typography-test
  (:require [statique.markdown.markdown :as md]
            [clojure.test :refer [deftest is]]))

(defn- render
  [text]
  (:body (md/transform text)))

(deftest en-dash
  (is (= "<p>foo – bar</p>\n" (render "foo -- bar"))))

(deftest em-dash
  (is (= "<p>foo — bar</p>\n" (render "foo --- bar"))))

(deftest dashes-untouched-in-code
  (is (= "<p>foo <code>a -- b</code> bar</p>\n" (render "foo `a -- b` bar")))
  (is (= "<pre><code>x -- y\n</code></pre>\n" (render "```\nx -- y\n```"))))
