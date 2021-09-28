(ns movie.backend.config
  (:require [environ.core :as environ]
            [movie.common.config :as config]))

(defn get-port []
  (Integer. (or (environ/env :port) 7600)))

(defn config
  ([]
   (config {:env (config/get-env)
            :port (get-port)}))
  ([{:keys [env port]}]
   {:port port
    :db (case env
          "dev" {:dbtype "postgresql"
                 :classname "org.postgresql.Driver"
                 :subprotocol "postgres"
                 :host "localhost"
                 :port 7601
                 :dbname "postgres"
                 :user "postgres"
                 :password "postgres"
                 :log? true}
          "prod" {:url (or (environ/env "DATABASE_URL")
                           (throw (ex-info "DATABASE_URL not set" {})))}
          (throw (ex-info "Invalid env" {:env env})))
    :tmdb {:url "https://api.themoviedb.org/3"
           :key "7197608cef1572f5f9e1c5b184854484"
           :retry-options {:initial-wait 0
                           :max-attempts 3}}}))
