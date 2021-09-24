(ns movie.cli.main
  (:require [movie.cli.config :as config]
            [movie.cli.core :as core]))

(defn -main [[cmd]]
  (let [config (config/config)]
    (case cmd
      "sync" (core/sync config)
      (throw (ex-info "Invalid command" {:cmd cmd})))))
