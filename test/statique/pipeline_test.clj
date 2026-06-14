(ns statique.pipeline-test
  (:require [clojure.test :refer [deftest is testing]]
            [statique.pipeline :as p]
            [statique.config :as cfg]))

(defn- stable-noembed []
  (let [data {}]
    (fn [k] (case k :all data :hash (hash data)))))

(defn- make-context []
  {:config {} :noembed (stable-noembed)})

(defn- items-cache-written? [state]
  (let [written (atom false)]
    (with-redefs [cfg/dump-cache (fn [_ name _] (when (= name "items") (reset! written true)))]
      (p/execute (p/->WriteCacheStage) (make-context) state))
    @written))

(deftest write-cache-stage
  (testing "new item triggers cache save"
    (is (items-cache-written? {"slug1" {:changed? true :source-crc 123}})))

  (testing "changed cached item triggers cache save"
    (is (items-cache-written? {"slug1" {:changed? true :source-crc 456 :transformed {:title "Updated"}}})))

  (testing "new draft triggers cache save"
    (is (items-cache-written? {"draft1" {:cache-dirty? true :source-crc 111 :transformed {:draft "true"}}})))

  (testing "draft message update triggers cache save"
    (is (items-cache-written? {"draft1" {:cache-dirty? true :source-crc 222 :transformed {:draft "remove to publish"}}})))

  (testing "unchanged items do not trigger cache save"
    (is (not (items-cache-written? {"slug1" {:source-crc 123 :transformed {:title "Hello"}}})))))
