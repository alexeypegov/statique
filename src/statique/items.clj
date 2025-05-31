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
   (item-seq 0 0 item-type 1 [] [] col))
  ([page-size feed-size col]
   (item-seq page-size feed-size :item 1 [] [] col))
  ([page-size feed-size item-type page-index page-items feed-items col]
   (lazy-seq
    (cond
       ;; page when full
      (and (pos? page-size) (= page-size (count page-items)))
      (cons (make-page page-items page-index (boolean (seq col)))
            (item-seq page-size feed-size item-type (inc page-index) [] feed-items col))

       ;; feed then full
      (and (pos? feed-size) (= feed-size (count feed-items)))
      (cons (make-feed feed-items)
            (item-seq page-size -1 item-type page-index page-items [] col))

       ;; next item
      (seq col)
      (let [item (first col)]
        (cons (make-item item-type item)
              (item-seq page-size feed-size item-type page-index
                        (conj page-items item) (conj feed-items item) (rest col))))

      :else
      (concat
       (when (and (pos? page-size) (seq page-items))
         [(make-page page-items page-index false)])
       (when (and (pos? feed-size) (seq feed-items))
         [(make-feed feed-items)]))))))
