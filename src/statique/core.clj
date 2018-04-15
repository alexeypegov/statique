(ns statique.core
  (:require [slingshot.slingshot :only [throw+]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [statique.builder :as builder]
            [statique.logging :as log]
            [yaml.core :as yaml])
  (:gen-class))

(def config-name         "blog.yaml")
(def convert-to-symbols  true)

(defn long-string [& strings] (string/join "\n" strings))

(defn valid-dir?
  [path]
  (let [file (io/file path)]
    (and
      (.isDirectory file)
      (.exists (io/file path config-name)))))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn parse-config
  [base]
  (let [config-file (io/file base config-name)]
    (yaml/from-file config-file convert-to-symbols)))

(defn build
  [root-dir]
  (builder/generate root-dir (parse-config root-dir)))

(defn -main
  [& args]
  (log/set-level :debug)
  (print "yay"))