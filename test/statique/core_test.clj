(ns statique.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [statique.core :as core]
            [statique.util :as u]
            [clojure.tools.logging :as log]))

(defn test-fixture [f]
  ; Clean up any global state before each test
  (f))

(use-fixtures :each test-fixture)

(deftest file-writer-test
  (testing "file-writer returns file when item changed, nil when not"
    (let [wrote-files (atom [])
          file-writer #'core/file-writer]
      (with-redefs [u/write-file (fn [file data] (swap! wrote-files conj file))]
        ; Test unchanged item
        (is (nil? (file-writer {:changed? false :target-file "test.html" :rendered "content"})))
        
        ; Test changed item
        (is (= "test.html" (file-writer {:changed? true :target-file "test.html" :rendered "content"})))
        (is (= ["test.html"] @wrote-files))))))

(deftest most-recent-item-test
  (testing "most-recent-item finds the item with the highest slug"
    (let [most-recent-item #'core/most-recent-item
          items {"item1" {:type :item :transformed {:slug "2020-01-01-first"}}
                 "item2" {:type :item :transformed {:slug "2020-12-31-last"}}
                 "item3" {:type :item :transformed {:slug "2020-06-15-middle"}}
                 "page1" {:type :page}}]
      (is (= {:type :item :transformed {:slug "2020-12-31-last"}}
             (most-recent-item items))))))

(deftest prepare-cache-test
  (testing "prepare-cache removes render-specific keys"
    (let [prepare-cache #'core/prepare-cache
          item {:source-crc 123 :rendered "html" :target-file "file.html" :changed? true :other-key "value"}
          result (prepare-cache {} ["key" item])]
      (is (= {"key" {:source-crc 123 :other-key "value"}} result)))))