(ns movie.backend.main
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [movie.backend.config :as config]
            [movie.backend.system :as system]
            [taoensso.timbre :as log]))

(defn -main []
  (let [config (config/config)]
    (log/info "Starting server.")
    (component/start-system (system/system config))
    @(promise)))
