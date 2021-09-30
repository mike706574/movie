(ns movie.backend.config
  (:require [movie.common.config :as config]))

(def port-var "PORT")

(defn get-port []
  (Integer. (or (config/get-env-var port-var) 7600)))

(def admin-password-var "ADMIN_PASSWORD")

(defn get-admin-password []
  (println "ADMIN PASSWORD" (System/getenv "ADMIN_PASSWORD"))
  (or (config/get-env-var admin-password-var)
      (throw (ex-info "Admin password environment variable not set."
                      {:var admin-password-var}))))

(defn config
  ([]
   (config {:admin-password ()
            :env (config/get-env)
            :port (get-port)}))
  ([{:keys [admin-password env port]}]
   {:port port
    :admin-password admin-password
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
          "prod" {:jdbcUrl (or (config/get-env-var "JDBC_DATABASE_URL")
                               (throw (ex-info "JDBC_DATABASE_URL not set" {})))}
          (throw (ex-info "Invalid env" {:env env})))
    :tmdb {:url "https://api.themoviedb.org/3"
           :key "7197608cef1572f5f9e1c5b184854484"
           :retry-options {:initial-wait 0
                           :max-attempts 3}}}))
