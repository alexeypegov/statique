(ns statique.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [statique.pipeline :as pipeline]
            [statique.util :as u]
            [clojure.tools.logging :as log]))

(defn test-fixture [f]
  ; Clean up any global state before each test
  (f))

(use-fixtures :each test-fixture)

(deftest write-output-stage-test
  (testing "WriteOutputStage returns state unchanged"
    (let [stage (pipeline/->WriteOutputStage)
          state {"item1" {:changed? false :target-file "unchanged.html" :rendered "content1"}
                 "item2" {:changed? true :target-file "changed.html" :rendered "content2"}}]
      (with-redefs [u/write-file (fn [_file _data] nil)]  ; Mock to prevent actual file writes
        (let [result (pipeline/execute stage nil state)]
          (is (= state result)))))))

(deftest copy-index-stage-test
  (testing "CopyIndexStage returns state unchanged"
    (let [stage (pipeline/->CopyIndexStage)
          context (pipeline/create-context {:general {:copy-last-as-index false}} nil nil nil nil)
          state {"item1" {:type :item :changed? true :target-file (java.io.File. "2020-01-01-first.html")
                          :transformed {:slug "2020-01-01-first"} :rendered "content1"}}]
      (with-redefs [u/write-file (fn [_file _data] nil)]  ; Mock to prevent actual file writes
        (let [result (pipeline/execute stage context state)]
          (is (= state result)))))))

(deftest prepare-cache-stage-test
  (testing "PrepareCacheStage removes render-specific keys"
    (let [stage (pipeline/->PrepareCacheStage)
          state {"key" {:source-crc 123 :rendered "html" :target-file "file.html" :changed? true :other-key "value"}}
          result (pipeline/execute stage nil state)]
      (is (= {"key" {:source-crc 123 :other-key "value"}} result))))

  (testing "PrepareCacheStage preserves prev/next links"
    (let [stage (pipeline/->PrepareCacheStage)
          state {"note1" {:source-crc 123 :rendered "html" :target-file "file.html" :changed? true
                          :prev "note0" :next "note2" :count 5 :type :item}}
          result (pipeline/execute stage nil state)]
      (is (= {"note1" {:source-crc 123 :prev "note0" :next "note2" :count 5 :type :item}} result)
          "PrepareCacheStage should preserve prev/next links for next/previous navigation"))))
