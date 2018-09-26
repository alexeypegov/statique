(defproject statique "0.1.0-SNAPSHOT"
  :description "Static blog generator"
  :url "http://github.com/alexeypegov/statique"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [slingshot "0.12.2"]
                 [io.forward/yaml "1.0.6"]
                 [com.atlassian.commonmark/commonmark "0.11.0"]
                 [com.atlassian.commonmark/commonmark-ext-yaml-front-matter "0.11.0"]
                 [com.atlassian.commonmark/commonmark-ext-gfm-strikethrough "0.11.0"]
                 [org.freemarker/freemarker "2.3.27-incubating"]
                 [http-kit "2.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [me.raynes/fs "1.4.6"]
                 [clj-time "0.14.4"]
                 [org.clojure/tools.cli "0.3.7"]
                 [pandect "0.6.1"]]
  :plugins [[lein-pprint "1.2.0"]
            [lein-difftest "2.0.0"]
            [lein-zprint "0.3.7"]
            [lein-eftest "0.4.3"]
            [lein-bin "0.3.4"]]
  :profiles {:dev {:dependencies [[clj-stacktrace "0.2.8"]]
                   :jvm-opts ["-Ddebug=true"]}}
  :repl-options {:init-ns statique.core
                 :caught clj-stacktrace.repl/pst+}
  :main statique.core
  :bin {:name           "statique"
        :bin-path       "~/bin"
        :bootclasspath  true}
  :aot [statique.media statique.core])
