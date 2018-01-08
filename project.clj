(defproject static "0.1.0-SNAPSHOT"
  :description "Static blog generator"
  :url "http://github.com/alexeypegov/static"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [slingshot "0.12.2"]
                 [io.forward/yaml "1.0.6"]
                 [org.clojure/tools.cli "0.3.5"]]
  :plugins [[lein-pprint "1.2.0"]
            [lein-difftest "2.0.0"]]
  :profiles {:dev {:dependencies [[clj-stacktrace "0.2.8"]]}}
  :repl-options {:init-ns b5v.core
                 :caught clj-stacktrace.repl/pst+}
  :main static.core
  :aot [static.core])
