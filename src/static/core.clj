(ns static.core
  (:use [slingshot.slingshot :only [throw+]]
        [clojure.tools.cli :only [parse-opts]]
        [clojure.java.io :as io])
  (:gen-class))

(defn long-string [& strings] (clojure.string/join "\n" strings))

(defn valid-dir
  [path]
  (let [file (io/file path)]
    (and
      (.isDirectory file)
      (.exists (io/file path "blog.yaml")))))

(def cli-options
  [["-w" "--watch" "Watch for changes"
    :default false
    :parse-fn #(Boolean/parseBoolean %)]
   ["-h" "--help" "This help message"]])

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn usage
  [summary]
  (long-string "Usage: bgen [options] [blog directory]"
               "Where [blog directory] is a directory containing blog.yaml file (current directory is used by default)"
               ""
               "And options are:"
               summary))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) {:message (usage summary) :ok? true}
      (= 1 (count arguments)) (if (valid-dir (first arguments))
                                {:working-dir (first arguments) :options options}
                                {:message "Please, specify directory containing blog.yaml" :ok? false})
      :else {:working-dir "." :options options :ok? true})))

(defn build
  [working-dir options]
  (println working-dir))

(defn -main
  [& args]
  (let [{:keys [message working-dir options ok?]} (validate-args args)]
    (if message
      (exit (if ok? 0 1) message)
      (build working-dir options))))