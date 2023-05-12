(ns statique.notes-test
  (:require [clojure.java.io :as io]
            [statique.notes :as n]
            [statique.config :as cfg]
            [statique.util :as u]
            [clojure.test :refer [deftest]]))

(def ^:private working-dir (io/file "/working-dir/"))

#_(defn setup [f]
  (with-redefs [cfg/general (fn [key] (key {:root-dir        working-dir
                                            :date-format     "yyyy-MM-dd"
                                            :tz              "Europe/Moscow"
                                            :output-dir      (io/file working-dir "out/")
                                            :index-page-name "index"}))
                u/crc32     (constantly 777)]
    (f)))

#_(use-fixtures :once setup)

#_(deftest item-changed?
  (let [item-changed? #'n/item-changed?]
    (are [result source-crc-current target-crc-current item] (= result (item-changed? :nope item source-crc-current target-crc-current))
      true  1 nil {}
      true  2 nil {:source-crc 1}
      true  1 2   {:source-crc 1}
      true  1 2   {:source-crc 1 :target-crc 1}
      false 1 2   {:source-crc 1 :target-crc 2})))

#_(deftest make-item-map
  (with-redefs [clj-uuid/v3 (constantly 1)]
    (let [make-item-map #'n/make-item-map]
      (is (= {:source-file     (io/file working-dir "notes/some_file.md")
              :source-relative "notes/some_file.md"
              :target-file     (io/file working-dir "out/some_file.html")
              :target-relative "out/some_file.html"
              :source-crc      777
              :target-crc      777
              :changed         true
              :slug            "some_file"
              :link            "/some_file.html"
              :uuid            1}
             (make-item-map (constantly nil) :nope (io/file working-dir "notes/some_file.md"))))
      (is (= {:source-file     (io/file working-dir "notes/some_unchanged.md")
              :source-relative "notes/some_unchanged.md"
              :target-file     (io/file working-dir "out/some_unchanged.html")
              :target-relative "out/some_unchanged.html"
              :slug            "some_unchanged"
              :link            "/some_unchanged.html"
              :uuid            1
              :source-crc      777
              :target-crc      777
              :changed         false}
             (make-item-map (constantly {:source-crc 777 :target-crc 777}) :nope (io/file working-dir "notes/some_unchanged.md")))))))

#_(deftest page-changed?
  (let [page-changed? #'n/page-changed?]
    (are [result target-crc-current items-crc-current items page] (= result (page-changed? page items target-crc-current items-crc-current))
      true  1 nil [] {}
      true  2 nil [] {:target-crc 1}
      true  1 1   [] {:target-crc 1}
      true  1 2   [] {:target-crc 1
                      :items-hash 1}
      true  1 2   '({:changed true}) {:target-crc 1
                                      :items-hash 2}
      false 1 2   '({:changed false}
                    {:changed false}) {:target-crc 1
                                       :items-hash 2})))

#_(deftest make-page
  (with-redefs [cfg/notes-cache (delay {:pages {1 {:target-crc 666}}})]
    (let [make-page #'n/make-page]
      (is (= {:type            :page
              :index           1
              :items           '({:slug "slug1"} {:slug "slug2"})
              :target-file     (io/file working-dir "out/index.html")
              :target-relative "out/index.html"
              :items-hash      -346581602
              :changed         true
              :target-crc      777}
             (make-page {:index 1
                         :items '({:slug "slug1"}
                                  {:slug "slug2"})}))))))

#_(deftest feed-changed?
  (let [feed-changed? #'n/feed-changed?]
    (with-redefs [me.raynes.fs/exists? (fn [f] (= "exists.xml" f))]
      (are [result target-crc-current feed] (= result (feed-changed? feed target-crc-current))
        true  1 {}
        true  1 {:target-crc 2}
        false 1 {:target-crc 1}))))

#_(deftest make-feed
  (with-redefs [cfg/notes-cache (delay {:feeds {"rss" {:target-crc 666}}})]
    (let [make-feed #'n/make-feed]
      (is (= {:type            :feed
              :name            "rss"
              :target-file     (io/file working-dir "out/rss.xml")
              :target-relative "out/rss.xml"
              :changed         true
              :target-crc      777}
             (make-feed "rss"))))))

#_(deftest format-dates
  (let [prepare-dates #'n/prepare-dates]
    (is (= {:created-at   "2020-04-22T00:00:00+03:00"}
           (prepare-dates "2020-04-22" "Europe/Moscow")))))

#_(deftest check-error
  (let [check-error #'n/check-error]
    (with-redefs [u/exit (fn [code message] (str code " " message))]
      (is (= "-1 something"
             (check-error {:status :error :message "something" :model {:fail true}})))
      (is (= "yay"
             (check-error {:status :ok :result "yay"}))))))
