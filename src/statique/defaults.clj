(ns statique.defaults)

(def ^:private default-config
  {:general {:notes           "notes/"
             :theme           "theme/"
             :output          "./out/"
             :cache           "cache/"
             :notes-per-page  10
             :date-format     "yyyy-MM-dd"
             :tz              "Europe/Moscow"
             :base-url        "/"
             :rss             {:count 10
                               :feeds ["rss"]}
             :copy            nil
             :pages           nil
             :keep            nil}
   :vars {}})

(defn with-defaults
  [config]
  (merge-with into default-config config))