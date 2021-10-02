(ns user
  (:require [aleph.http :as http]
            [buddy.hashers :as auth-hashers]
            [clojure.set :as set]
            [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [movie.common.client :as client]
            [movie.common.storage :as storage]
            [movie.common.tmdb :as tmdb]
            [movie.backend.db :as db]
            [movie.backend.repo :as repo]
            [movie.backend.system :as system]
            [movie.backend.config :as backend-config]
            [movie.cli.config :as cli-config]
            [movie.cli.core :as core]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [taoensso.timbre :as log]))

(repl/disable-reload!)

(repl/set-refresh-dirs "src")

(def port 7600)

;; backend
(def admin-password "admin!")
(def hashed-admin-password (auth-hashers/derive admin-password))

(def config (backend-config/config {:admin-password hashed-admin-password
                                    :env "dev"
                                    :port port}))

(defonce system nil)

(defn init []
  (alter-var-root #'system (constantly (system/system config)))
  :init)

(defn start []
  (try
    (alter-var-root #'system component/start-system)
    :started
    (catch Exception ex
      (log/error (or (.getCause ex) ex) "Failed to start system.")
      :failed)))

(defn stop []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop-system s))))
  :stopped)

(defn go []
  (init)
  (start)
  :ready)

(defn reset []
  (stop)
  (repl/refresh :after `go))

(defn restart []
  (stop)
  (go))

(def db (db/new-db (:db config)))
(def tmdb (tmdb/client (:tmdb config)))

(def cli-config (cli-config/config {:env "dev" :password admin-password}))
(def deps (cli-config/deps cli-config))

(def client (client/client (:client cli-config)))

(def test-dir "movies")

(def test-movies
  [{:title "Aladdin", :video-files ["Aladdin.mp4"] :letter "A"}
   {:title "Airplane", :video-files ["Airplane.mp4"] :letter "A"}
   {:title "Akira", :video-files ["Akira.mp4"] :letter "A"}
   {:title "Beauty and the Beast", :video-files ["Beauty and the Beast.mp4"] :letter "B"}])

(def new-test-movie
  {:title "Mulan" :video-files ["Mulan.mp4"] :letter "M"})

(defn register-users []
  (client/register client "mike" "mike!")
  (client/register client "abby" "abby!"))


(defn sim-1 []
  (db/reset db)
;;  (register-users)
  (storage/mock-dir! test-dir [(nth test-movies 3)])
  (core/sync-movies! deps))

(defn sim-2 []
  (storage/mock-movie! test-dir new-test-movie)
  (core/sync-movies! deps)
  )

(comment

  ;; tmdb
  (tmdb/get-config tmdb)
  (-> (tmdb/get-movie tmdb 10020) :body keys set)
  (-> (tmdb/search-movies tmdb "Beauty and the Beast") :body first)

  (tmdb/get-movie tmdb 10020)

  ;; db
  (db/rollback db)
  (db/collect-tables db)
  (db/migrations db)
  (db/reset db)

  ;; storage
  (storage/mock-dir! test-dir test-movies)
  (storage/mock-movie! test-dir new-test-movie)
  (storage/populate-movie-metadata! test-dir)

  ;; repo
  (repo/clear-movies! db)
  (repo/insert-movies! db (storage/read-movies-dir test-dir))
  (repo/list-movies db)

  (def akira (:uuid (repo/get-movie db {:title "Akira"})))

  (repo/get-movie db {:uuid akira})
  (repo/get-movie-id db akira)

  (repo/rate-movie! db akira 3.5)

  ;; client
  (client/list-movies client)
  (client/sync-movies! client (storage/read-movies-dir test-dir))

  ;; core
  (core/sync-movies! deps)
  (core/list-movies deps)
)

(def prod-db
  (jdbc/get-datasource "TODO"))

(def prod-cli-config (cli-config/config {:env "prod" :password "TODO"}))

(def prod-deps (cli-config/deps prod-cli-config))

(comment
  (backend-config/config)
  (core/sync-movies! prod-deps)

  (db/reset prod-db)
  )
