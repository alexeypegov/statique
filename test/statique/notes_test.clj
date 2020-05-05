(ns statique.notes-test
  (:require [clojure.java.io :as io]
            [statique.notes :as n])
  (:use clojure.test))

(def ^:private working-dir (io/file "/working-dir/"))

(defn setup [f]
  (with-redefs [statique.context/root-dir   working-dir
                statique.context/output-dir (io/file working-dir "out/")
                statique.context/vars       {}
                statique.context/base-url   "/"
                statique.util/crc32         (constantly 777)]
    (f)))

(use-fixtures :once setup)

(deftest item-changed?
  (let [item-changed? #'statique.notes/item-changed?]
    (are [result source-crc-current target-crc-current item] (= result (item-changed? item source-crc-current target-crc-current))
      true  1 nil {}
      true  2 nil {:source-crc 1}
      true  1 2   {:source-crc 1}
      true  1 2   {:source-crc 1 :target-crc 1}
      false 1 2   {:source-crc 1 :target-crc 2})))

(deftest make-item-map
  (with-redefs [clj-uuid/v3 (constantly 1)]
    (let [make-item-map #'statique.notes/make-item-map]
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
             (make-item-map (constantly nil) (io/file working-dir "notes/some_file.md"))))
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
             (make-item-map (constantly {:source-crc 777 :target-crc 777}) (io/file working-dir "notes/some_unchanged.md")))))))

(deftest page-changed?
  (let [page-changed? #'statique.notes/page-changed?]
    (are [result target-crc-current items-crc-current page] (= result (page-changed? page target-crc-current items-crc-current))
      true  1 nil {}
      true  2 nil {:target-crc 1}
      true  1 1   {:target-crc 1}
      true  1 2   {:target-crc 1
                   :items-hash 1}
      true  1 2   {:target-crc 1
                   :items-hash 2
                   :items      '({:changed true})}
      false 1 2   {:target-crc 1
                   :items-hash 2
                   :items      '({:changed false}
                                 {:changed false})})))

(deftest make-page
  (with-redefs [statique.context/notes-cache {:pages {1 {:target-crc 666}}}]
    (let [make-page #'statique.notes/make-page]
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

(deftest feed-changed?
  (let [feed-changed? #'statique.notes/feed-changed?]
    (with-redefs [me.raynes.fs/exists? (fn [f] (= "exists.xml" f))]
      (are [result target-crc-current target-file feed] (= result (feed-changed? feed target-file target-crc-current))
        true  1 nil              {}
        true  1 nil              {:target-crc 2}
        true  1 "not-exists.xml" {:target-crc 1}
        false 1 "exists.xml"     {:target-crc 1}))))

(deftest make-feed
  (with-redefs [statique.context/notes-cache {:feeds {"rss" {:target-crc 666}}}]
    (let [make-feed #'statique.notes/make-feed]
      (is (= {:type            :feed
              :name            "rss"
              :target-file     (io/file working-dir "out/rss.xml")
              :target-relative "out/rss.xml"
              :changed         true
              :target-crc      777}
             (make-feed "rss"))))))

(deftest format-dates
  (let [format-dates #'statique.notes/format-dates]
    (is (= {:rfc-822  "Wed, 22 Apr 2020 00:00:00 +0300"
            :rfc-3339 "2020-04-22T00:00:00+03:00"}
           (format-dates "2020-04-22")))))

(deftest make-cache
  (let [make-cache #'statique.notes/make-cache]
    (is (= {:pages {1 {:items-hash 1
                       :target-crc 1
                       :rendered   "html"}}
            :notes {"note1" {:rendered   "html"
                             :source-crc 1
                             :target-crc 3}
                    "note2" {:rendered   "html"
                             :source-crc 33
                             :target-crc 35}}}
           (make-cache {} {:index                 1
                           :target-crc            1
                           :items-hash            1
                           :some-unknown-property "yay"
                           :rendered              "html"
                           :items                 '({:source-relative "note1"
                                                     :source-crc      1
                                                     :target-crc      3
                                                     :something-else  5
                                                     :rendered        "html"}
                                                    {:source-relative  "note2"
                                                     :source-crc       33
                                                     :target-crc       35
                                                     :ignored-property "blahblah"
                                                     :rendered         "html"})})))
    (is (= {:pages {2 {}}
            :notes {}
            :feeds {"rss"  {:target-crc 1
                            :rendered   "xml"}
                    "atom" {:target-crc 4
                            :rendered   "xml"}}}
           (make-cache {} {:index 2
                           :feeds '({:name           "rss"
                                     :target-crc     1
                                     :rendered       "xml"}
                                    {:name             "atom"
                                     :target-crc       4
                                     :rendered         "xml"
                                     :unknown-property "yeah"})})))))

(deftest check-error
  (let [check-error #'statique.notes/check-error]
    (with-redefs [statique.util/exit (fn [code] (str "exited code=" code))]
      (is (= "exited code=-1"
             (check-error {:status :error :message "something" :model {:fail true}})))
      (is (= "yay"
             (check-error {:status :ok :result "yay"}))))))
