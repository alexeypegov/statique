(defproject statique "0.65"
  :description "Statique — static blog generator"
  :url "https://github.com/alexeypegov/statique"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [io.forward/yaml "1.0.11"]
                 [org.freemarker/freemarker "2.3.34"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/tools.logging "1.3.1"]
                 [org.clojure/tools.cli "1.4.256"]
                 [ch.qos.logback/logback-classic "1.5.32"]
                 [org.commonmark/commonmark "0.28.0"]
                 [org.commonmark/commonmark-ext-yaml-front-matter "0.28.0"]
                 [org.commonmark/commonmark-ext-gfm-strikethrough "0.28.0"]
                 [http-kit "2.8.1"]
                 [org.clojure/data.json "2.5.2"]
                 [pandect "1.0.2"]
                 [danlentz/clj-uuid "0.2.5"]]
  :plugins [[lein-eftest "0.5.9"]
            [lein-ancient "1.0.0-RC3"]]
  :repl-options {:init-ns statique.core}
  :aot [statique.markdown.media statique.core]
  :main statique.core)
