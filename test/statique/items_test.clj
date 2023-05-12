(ns statique.items-test
  (:require [clojure.test :refer [deftest is]]
            [statique.items :as i]))

(deftest item-seq
  (is (= '({:slug 0, :type :item}
           {:slug 1, :type :item}
           {:slug 2, :type :item}
           {:slug 3, :type :item}
           {:slug 4, :type :item}
           {:index 1, :items [0 1 2 3 4], :type :page, :next? false}
           {:items [0 1 2 3 4], :type :feed}) (i/item-seq 5 5 (range 5))))
  (is (= '({:slug 0, :type :item}
           {:index 1, :items [0], :type :page, :next? false}
           {:items [0], :type :feed}) (i/item-seq 5 5 (range 1))))
  (is (= '({:slug 0, :type :item}
           {:slug 1, :type :item}
           {:index 1, :items [0 1], :type :page, :next? true}
           {:slug 2, :type :item}
           {:items [0 1 2], :type :feed}
           {:slug 3, :type :item}
           {:index 2, :items [2 3], :type :page, :next? false}) (i/item-seq 2 3 (range 4))))
  (is (= '({:slug 0, :type :item}
           {:slug 1, :type :item}
           {:slug 2, :type :item}
           {:index 1, :items [0 1 2], :type :page, :next? true}
           {:items [0 1 2], :type :feed}
           {:slug 3, :type :item}
           {:slug 4, :type :item}
           {:slug 5, :type :item}
           {:index 2, :items [3 4 5], :type :page, :next? false}) (i/item-seq 3 3 (range 6))))
  (is (= '({:slug 0, :type :item}
           {:slug 1, :type :item}
           {:slug 2, :type :item}) (i/item-seq 0 0 (range 3)))))


