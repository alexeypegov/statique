(ns statique.notes-test
  (:require [clojure.java.io :as io]
            [statique.notes :as n]
            [statique.config :as cfg]
            [statique.markdown.markdown :as md]
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
                                        :first-page-as-index true
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

(deftest ^:eftest/synchronized deleted-slug-test
  (testing "deleted-slug? uses cached value when CRC matches"
    (let [deleted-cache {"my-post" {:source-crc 777 :transformed {:deleted "outdated"}}}
          normal-cache  {"my-post" {:source-crc 777 :transformed {:title "Normal"}}}]
      (is (true?  (#'n/deleted-slug? "notes/" deleted-cache "my-post")))
      (is (false? (#'n/deleted-slug? "notes/" normal-cache  "my-post")))))

  (testing "deleted-slug? parses front matter when CRC has changed"
    (with-redefs [md/metadata (constantly {:deleted "reason"})]
      (is (true? (#'n/deleted-slug? "notes/" {"my-post" {:source-crc 999}} "my-post"))))

    (with-redefs [md/metadata (constantly {:title "Normal"})]
      (is (false? (#'n/deleted-slug? "notes/" {"my-post" {:source-crc 999}} "my-post")))))

  (testing "deleted-slug? parses front matter when slug not in cache"
    (with-redefs [md/metadata (constantly {:deleted "reason"})]
      (is (true? (#'n/deleted-slug? "notes/" {} "my-post"))))

    (with-redefs [md/metadata (constantly {:title "Normal"})]
      (is (false? (#'n/deleted-slug? "notes/" {} "my-post"))))))

(deftest sitemap-item-test
  (testing "sitemap-item returns nil for pages and feeds"
    (let [config {}]
      (is (nil? (n/sitemap-item config {:type :page})))
      (is (nil? (n/sitemap-item config {:type :feed})))))

  (testing "sitemap-item creates sitemap entry for items"
    (let [config {}
          props {:type :item :transformed {:slug "test-post"}}]
      (is (= {:slug "test-post" :loc "/test-post.html"}
             (n/sitemap-item config props)))))

  (testing "sitemap-item returns nil for deleted items"
    (let [config {}
          props {:type :item :transformed {:slug "old-post" :deleted "outdated content"}}]
      (is (nil? (n/sitemap-item config props)))))

  (testing "sitemap-item returns nil for draft items"
    (let [config {}
          props {:type :item :transformed {:slug "wip-post" :draft "coming soon"}}]
      (is (nil? (n/sitemap-item config props))))))

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

(deftest ^:eftest/synchronized draft-slug-test
  (testing "draft-slug? uses cached value when CRC matches"
    (let [draft-cache  {"my-post" {:source-crc 777 :transformed {:draft "WIP"}}}
          normal-cache {"my-post" {:source-crc 777 :transformed {:title "Normal"}}}]
      (is (true?  (#'n/draft-slug? "notes/" draft-cache  "my-post")))
      (is (false? (#'n/draft-slug? "notes/" normal-cache "my-post")))))

  (testing "draft-slug? parses front matter when CRC has changed"
    (with-redefs [md/metadata (constantly {:draft "WIP"})]
      (is (true? (#'n/draft-slug? "notes/" {"my-post" {:source-crc 999}} "my-post"))))
    (with-redefs [md/metadata (constantly {:title "Normal"})]
      (is (false? (#'n/draft-slug? "notes/" {"my-post" {:source-crc 999}} "my-post")))))

  (testing "draft-slug? parses front matter when slug not in cache"
    (with-redefs [md/metadata (constantly {:draft "WIP"})]
      (is (true? (#'n/draft-slug? "notes/" {} "my-post"))))
    (with-redefs [md/metadata (constantly {:title "Normal"})]
      (is (false? (#'n/draft-slug? "notes/" {} "my-post"))))))

(defn- without-runtime-keys [cache]
  (reduce (fn [r [k v]] (assoc r k (dissoc v :rendered :target-file :changed?))) {} cache))

(deftest ^:eftest/synchronized generate-notes-deleted-behavior-test
  (let [live-a   (io/file "notes/2024-01-03-live-a.md")
        live-b   (io/file "notes/2024-01-02-live-b.md")
        deleted  (io/file "notes/2024-01-01-deleted.md")
        contents {"2024-01-03-live-a"  "# Live A"
                  "2024-01-02-live-b"  "# Live B"
                  "2024-01-01-deleted" "---\nDeleted: gone\n---\n# Deleted"}]
    (with-redefs [cfg/get-general  (fn [_cfg k]
                                     (get {:notes-dir        "notes/"
                                           :output-dir       (io/file working-dir "out/")
                                           :deleted-template "deleted"
                                           :note-template    "note"
                                           :page-template    "page"
                                           :index-page-name  "index"
                                           :page-prefix      "page-"
                                           :first-page-as-index true
                                           :notes-per-page   10
                                           :items-per-feed   10
                                           :feeds            ["atom"]
                                           :base-url         "/"
                                           :vars             {}} k))
                  u/sorted-files   (fn [_ _] [deleted live-b live-a])
                  slurp            (fn [f] (get contents (u/slug f) ""))]
      (let [page-slugs  (atom nil)
            render-tpls (atom [])
            ctx         {:config      {}
                         :reporter    (fn [& _] nil)
                         :transformer (fn [_type text] (md/transform text))
                         :renderer    (fn [tpl render-ctx]
                                        (when (= "page" tpl)
                                          (reset! page-slugs (mapv :slug (:items render-ctx))))
                                        (swap! render-tpls conj tpl)
                                        {:status :ok :result (str "rendered:" tpl)})}
            cache1      (n/generate-notes ctx {})]

        (testing "deleted note is excluded from pages"
          (is (= #{"2024-01-03-live-a" "2024-01-02-live-b"} (set @page-slugs))))

        (testing "deleted note is rendered independently with deleted-template"
          (is (= "rendered:deleted" (:rendered (get cache1 "2024-01-01-deleted"))))
          (is (contains? (set @render-tpls) "deleted")))

        (testing "deleted note is not re-rendered on second run when unchanged"
          (let [disk-cache (without-runtime-keys cache1)
                cache2     (n/generate-notes ctx disk-cache)]
            (is (nil? (:changed? (get cache2 "2024-01-01-deleted"))))))))))
