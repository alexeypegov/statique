(ns statique.defaults)

(def config
  {:general {:notes           "notes/"
             :theme           "theme/"
             :output          "./out/"
             :cache           "cache/"
             :notes-per-page  10
             :date-format     "yyyy-MM-dd"
             :base-url        "/"
             :rss             {:count 10}
             :copy            nil
             :pages           nil
             :keep            nil}
   :vars {}})