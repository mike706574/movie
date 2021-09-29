(ns movie.cli.main
  (:gen-class)
  (:require [clojure.pprint :as pprint]
            [movie.cli.config :as config]
            [movie.cli.core :as core]))

(defn -main [& [cmd]]
  (let [config (config/config)
        deps (config/deps config)]
    (case cmd
      "sync-movies" (pprint/pprint (core/sync-movies! deps))
      (throw (ex-info "Invalid command" {:cmd cmd})))))
