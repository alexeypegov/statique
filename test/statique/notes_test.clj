(ns statique.notes-test
  (:require [clojure.java.io :as io]
            [statique.notes :as n]
            [statique.config :as cfg]
            [statique.util :as u]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [clj-uuid :as uuid]))

(def ^:private working-dir (io/file "/working-dir/"))

(defn setup [f]
  (with-redefs [cfg/get-general (fn [_config key]
                                  (get {:notes-dir        "notes/"
                                        :output-dir       (io/file working-dir "out/")
                                        :singles-dir      "singles/"
                                        :index-page-name  "index"
                                        :page-prefix      "page-"
                                        :note-template    "note"
                                        :page-template    "page"
                                        :single-template  "single"
                                        :base-url         "/"
                                        :notes-per-page   10
                                        :items-per-feed   10
                                        :feeds            ["atom"]
                                        :vars             {}} key))
                u/crc32           (constantly 777)
                u/validate-dir    identity
                u/sorted-files    (constantly [])
                u/list-files      (constantly [])
                uuid/v3           (constantly "test-uuid")]
    (f)))

(use-fixtures :once setup)

(deftest item-transform-test
  (testing "item-transform creates proper item structure"
    (with-redefs [slurp (constantly "# Test\nContent")]
      (let [transformer (fn [_type text] {:content text :title "Test"})
            result (n/item-transform transformer :relative (io/file "test.md") "test-slug")]
        (is (= "test-slug" (:slug result)))
        (is (= "/test-slug.html" (:link result)))
        (is (= "test-uuid" (:uuid result)))
        (is (= "Test" (:title result)))
        (is (= "# Test\nContent" (:content result)))))))

(deftest page-filename-test
  (testing "page-filename generates correct filenames"
    (let [config {}]
      (is (= "index.html" (#'n/page-filename config 1)))
      (is (= "page-2.html" (#'n/page-filename config 2)))
      (is (= "page-10.html" (#'n/page-filename config 10))))))

(deftest items-changed-test
  (testing "items-changed? detects changes in items"
    (let [items {"a" {:changed? true}
                 "b" {:changed? false}
                 "c" {:changed? true}}]
      (is (true? (#'n/items-changed? ["a"] items)))
      (is (true? (#'n/items-changed? ["a" "b"] items)))
      (is (false? (#'n/items-changed? ["b"] items)))
      (is (false? (#'n/items-changed? [] items))))))

(deftest handler-protocol-test
  (testing "ItemHandler implements Handler protocol correctly"
    (let [config {}
          cached {:source-crc 777 :target-crc 777 :count 5}
          handler (n/->ItemHandler config "test-slug" 5 :note-template
                                   {:file (io/file "source.md") :crc 777} {:file (io/file "target.html") :crc 777}
                                   nil nil cached)]
      (is (= "test-slug" (n/id handler)))
      (is (false? (n/changed? handler)))

      (let [populated (n/populate handler {:title "Test"})]
        (is (= 777 (:source-crc populated)))
        (is (= 5 (:count populated))))))

  (testing "ItemHandler detects changes correctly"
    (let [config {}
          cached {:source-crc 555 :target-crc 777 :count 5}  ; Different source CRC
          handler (n/->ItemHandler config "test-slug" 5 :note-template
                                   {:file (io/file "source.md") :crc 777} {:file (io/file "target.html") :crc 777}
                                   nil nil cached)]
      (is (true? (n/changed? handler)))))

  (testing "PageHandler implements Handler protocol correctly"
    (let [config {}
          items {"slug1" {:changed? false} "slug2" {:changed? false}}
          cached {:target-crc 777 :items-hash 123}
          handler (n/->PageHandler config 1 ["slug1" "slug2"] items
                                   {:file (io/file "page.html") :crc 777} 123 cached)]
      (is (= 1 (n/id handler)))
      (is (false? (n/changed? handler)))))

  (testing "FeedHandler implements Handler protocol correctly"
    (let [config {}
          items {"slug1" {:changed? false}}
          cached {:target-crc 777 :items-hash 123}
          handler (n/->FeedHandler config "atom" ["slug1"] items
                                   {:file (io/file "atom.xml") :crc 777} 123 cached)]
      (is (= "atom" (n/id handler)))
      (is (false? (n/changed? handler))))))

(deftest sitemap-item-test
  (testing "sitemap-item returns nil for pages and feeds"
    (let [config {}]
      (is (nil? (n/sitemap-item config {:type :page})))
      (is (nil? (n/sitemap-item config {:type :feed})))))

  (testing "sitemap-item creates sitemap entry for items"
    (let [config {}
          props {:type :item :transformed {:slug "test-post"}}]
      (is (= {:slug "test-post" :loc "/test-post.html"}
             (n/sitemap-item config props))))))

(deftest prev-next-link-change-detection-test
  (testing "ItemHandler change detection for prev/next links"
    (let [config {}
          slug "note-a"
          count 5
          template :note-template
          source {:file (io/file "note-a.md") :crc 123}
          target {:file (io/file "note-a.html") :crc 456}

          ;; Cached item has specific prev/next links
          cached {:source-crc 123
                  :target-crc 456
                  :count 5}

          handler (n/->ItemHandler config slug count template source target nil nil cached)]

      (testing "handler reports no change when all cache values match"
        (is (false? (n/changed? handler))
            "Handler should not report change when source-crc, target-crc, count are same"))

      (testing "handler can detect prev/next link changes"
        ;; Test that ItemHandler correctly detects when prev/next links change
        (let [handler-with-different-prev (n/->ItemHandler config slug count template source target "different-prev" nil cached)
              handler-with-different-next (n/->ItemHandler config slug count template source target nil "different-next" cached)]
          (is (true? (n/changed? handler-with-different-prev))
              "Handler should detect change when prev link differs from cache")
          (is (true? (n/changed? handler-with-different-next))
              "Handler should detect change when next link differs from cache")))

      (testing "handler stores prev/next in cache"
        (let [handler-with-links (n/->ItemHandler config slug count template source target "prev-slug" "next-slug" cached)
              populated (n/populate handler-with-links {:title "Test"})]
          (is (= "prev-slug" (:prev populated))
              "Populated data should include prev link")
          (is (= "next-slug" (:next populated))
              "Populated data should include next link"))))))
