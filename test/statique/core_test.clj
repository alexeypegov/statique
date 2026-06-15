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


(deftest sitemap-model-test
  (testing "sitemap model ignores changed non-sitemap entries"
    (let [model (#'pipeline/sitemap-model
                 {:general {:base-url "/"}}
                 {"page1" {:type :page :changed? true}
                  "feed1" {:type :feed :changed? true}})]
      (is (false? (:changed? model)))
      (is (empty? (:items model)))
      (is (nil? (:most-recent-date model)))))

  (testing "sitemap model marks changed sitemap entries and derives most recent date"
    (let [model (#'pipeline/sitemap-model
                 {:general {:base-url "/"}}
                 {"first" {:type :item
                           :changed? false
                           :transformed {:slug "first" :date "2024-01-01"}}
                  "second" {:type :item
                            :changed? true
                            :transformed {:slug "second" :date "2024-01-02" :updated "2024-01-03"}}
                  "draft" {:type :item
                           :changed? true
                           :transformed {:slug "draft" :date "2024-01-04" :draft true}}})]
      (is (true? (:changed? model)))
      (is (= [{:slug "first" :date "2024-01-01" :loc "/first.html"}
              {:slug "second" :date "2024-01-02" :updated "2024-01-03" :loc "/second.html"}]
             (:items model)))
      (is (= "2024-01-03" (:most-recent-date model))))))

(deftest write-if-changed-test
  (testing "does not write when file exists and rendered content is unchanged"
    (let [writes (atom [])]
      (is (nil? (#'pipeline/write-if-changed
                 {:exists? (constantly true)
                  :crc32 (constantly 111)
                  :write-file (fn [file data] (swap! writes conj [file data]))}
                 (java.io.File. "/out" "sitemap.xml")
                 "<xml>same</xml>")))
      (is (empty? @writes))))

  (testing "writes when rendered content changed"
    (let [writes (atom [])
          target-file (java.io.File. "/out" "sitemap.xml")]
      (is (true? (#'pipeline/write-if-changed
                  {:exists? (constantly true)
                   :crc32 (fn [value] (if (= value "<xml>changed</xml>") 222 111))
                   :write-file (fn [file data] (swap! writes conj [file data]))}
                  target-file
                  "<xml>changed</xml>")))
      (is (= [[target-file "<xml>changed</xml>"]] @writes))))

  (testing "writes when target file does not exist"
    (let [writes (atom [])
          target-file (java.io.File. "/out" "sitemap.xml")]
      (is (true? (#'pipeline/write-if-changed
                  {:exists? (constantly false)
                   :crc32 (constantly 111)
                   :write-file (fn [file data] (swap! writes conj [file data]))}
                  target-file
                  "<xml>new</xml>")))
      (is (= [[target-file "<xml>new</xml>"]] @writes)))))
