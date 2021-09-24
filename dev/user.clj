(ns user
  (:require [aleph.http :as http]
            [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [movie.common.client :as client]
            [movie.common.storage :as storage]
            [movie.backend.db :as db]
            [movie.backend.repo :as repo]
            [movie.backend.system :as system]
            [movie.backend.config :as backend-config]
            [movie.backend.tmdb :as tmdb]
            [movie.cli.config :as cli-config]
            [movie.cli.core :as core]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [taoensso.timbre :as log]))

(repl/disable-reload!)

(repl/set-refresh-dirs "src")

(def port 7600)

;; backend
(def backend-config (backend-config/config {:env "dev" :port port}))

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

(def db (db/new-db (:db backend-config)))

(def cli-config (cli-config/config {:env "dev"}))
(def deps (cli-config/system cli-config))

(def tmdb (tmdb/client config))

(def client (client/client cli-config))

(def test-dir "movies")

(def test-movies
  [{:title "Aladdin", :video-files ["Aladdin.mp4"] :letter "A"}
   {:title "Airplane", :video-files ["Airplane.mp4"] :letter "A"}
   {:title "Akira", :video-files ["Akira.mp4"] :letter "A"}
   {:title "Beauty and the Beast", :video-files ["Beauty and the Beast.mp4"] :letter "B"}])

(def new-test-movie
  {:title "Mulan" :video-files ["Mulan.mp4"] :letter "M"})

(comment

  ;; tmdb
  (tmdb/get-config tmdb)

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
