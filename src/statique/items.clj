(ns statique.items)

(defn- make-item
  [item-type item]
  {:type item-type
   :slug item})

(defn- make-page
  [items index next?]
  {:type  :page
   :index index
   :next? next?
   :items items})

(defn- make-feed
  [items]
  {:type  :feed
   :items items})

(defn item-seq
  ([item-type col]
   (item-seq 0 0 item-type false 1 [] [] col))
  ([page-size feed-size col]
   (item-seq page-size feed-size :item (= page-size 0) 1 [] [] col))
  ([page-size feed-size item-type index-item page-index page-items feed-items col]
   (lazy-seq
    (cond
      (and (> page-size 0)
           (= page-size (count page-items))) (cons
                                              (make-page page-items page-index (not (empty? col)))
                                              (item-seq page-size feed-size item-type false (inc page-index) [] feed-items col))
      (and (> feed-size 0)
           (= feed-size (count feed-items))) (cons
                                              (make-feed feed-items)
                                              (item-seq page-size -1 item-type false page-index page-items [] col))
      :else (if (not-empty col)
              (let [item (first col)
                    rest (rest col)]
                (if index-item
                  (cons
                   (as-> (make-item item-type item) $
                     (assoc $ :real-slug (:slug $))
                     (assoc $ :slug "index"))
                   (item-seq page-size feed-size item-type false page-index page-items feed-items col))
                  (cons
                   (make-item item-type item)
                   (item-seq page-size feed-size item-type false page-index (conj page-items item) (conj feed-items item) rest))))
              (if (and (> page-size 0) (not-empty page-items))
                (cons
                 (make-page page-items page-index false)
                 (item-seq page-size feed-size item-type false page-index [] feed-items col))
                (when (and (> feed-size 0) (not-empty feed-items))
                  (cons
                   (make-feed feed-items)
                   (item-seq page-size feed-size item-type false page-index page-items [] col)))))))))