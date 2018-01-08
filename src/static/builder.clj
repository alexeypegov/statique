(ns static.builder
  (:use [slingshot.slingshot :only [throw+]]
        [clojure.tools.cli :only [parse-opts]]
        [clojure.java.io :as io])
  (:gen-class))
