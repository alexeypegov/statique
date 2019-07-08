(ns statique.core
  (:require [slingshot.slingshot :only [throw+]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [statique.builder :as builder]
            [statique.logging :as log]
            [statique.util :as u]
            [yaml.core :as yaml]
            [clojure.tools.cli :as cli]
            [me.raynes.fs :as fs])
  (:gen-class))

(def config-name         "blog.yaml")
(def convert-to-symbols  true)
(def version             (u/get-version 'statique))

(def cli-options
  [["-d" "--blog-dir DIR" "Blog dir"
   :default      (io/file (u/working-dir))
   :default-desc (u/working-dir)
   :parse-fn     fs/normalized]
   [nil "--debug" "Debug output"]
   [nil "--help" "Print this help"]])

(defn- usage
  [cli-summary]
  (u/long-string  ""
                  "Usage: statique [options] [command]"
                  ""
                  "Options:"
                  cli-summary
                  ""
                  "Commands:"
                  "  build  Build a blog. By default builds blog in the current dir."
                  "  new    Create a new note of today"
                  "  init   Create a new blog"
                  ""))

(defn- blog-dir?
  [path]
  (let [file (io/as-file path)]
    (and
      (.isDirectory file)
      (.exists (io/file path config-name)))))

(defn- parse-config
  [base]
  (-> (io/file base config-name)
      (yaml/from-file convert-to-symbols)))

(defn- build
  [root-dir]
  (builder/build (assoc (parse-config root-dir) :root root-dir)))

(defn- configure-debug-logging
  [options]
  (when (or
          (:debug options)
          (Boolean/parseBoolean (System/getProperty "debug")))
    (log/set-level :debug)))

(defn- validate-root
  [root]
  (if-not (blog-dir? root)
    (u/exit 1 (format "Can not find '%s' at '%s'" config-name (.getPath root)))
    (log/info (format "Root '%s'" (.getPath root)))))

(defn- execute-command
  [args options]
  (let [command (keyword (string/lower-case (first args)))
        root    (:blog-dir options)]
    (case command
      :build (do
        (validate-root root)
        (build root))
      (log/info (format "Unknown command '%s'" (first args))))))

(defn -main
  [& args]
  (printf "Statique %s" version)
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (u/exit 0 (usage summary))
      errors          (u/exit 1 errors))
    (configure-debug-logging options)
    (execute-command (if (empty? arguments) ["build"] arguments) options)
    (u/exit 0)))