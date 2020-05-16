(defproject statique "0.2.2"
  :description "Statique — static blog generator"
  :url "https://github.com/alexeypegov/statique"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [io.forward/yaml "1.0.9"]
                 [org.freemarker/freemarker "2.3.29"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/tools.logging "1.1.0"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [com.atlassian.commonmark/commonmark "0.14.0"]
                 [com.atlassian.commonmark/commonmark-ext-yaml-front-matter "0.14.0"]
                 [com.atlassian.commonmark/commonmark-ext-gfm-strikethrough "0.14.0"]
                 [http-kit "2.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [pandect "0.6.1"]
                 [clojure.java-time "0.3.2"]
                 [danlentz/clj-uuid "0.1.7"]
                 [clojure.java-time "0.3.2"]]
  :plugins [[lein-eftest "0.5.9"]
            [lein-binplus "0.6.6"]]
  :repl-options {:init-ns statique.core}
  :aot [statique.markdown.media statique.core]
  :bin {:name          "statique"
        :bin-path      "bin"
        :bootclasspath true}
  :main statique.core)
