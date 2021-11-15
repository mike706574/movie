(ns user
  (:require [aleph.http :as http]
            [buddy.hashers :as auth-hashers]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [movie.common.client :as client]
            [movie.common.config :as config]
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
(def tmdb (tmdb/new-client (:tmdb config)))

(def cli-config (cli-config/config {:env "dev" :password admin-password}))
(def deps (cli-config/deps cli-config))

(def client (client/new-client (:client cli-config)))

(def test-dir "movies")

(def test-movies
  [{:title "Aladdin" :video-files ["Aladdin.mp4"] :path "kids"}
   {:title "Airplane" :video-files ["Airplane.mp4"] :path "adults/a"}
   {:title "Akira" :video-files ["Akira.mp4"] :path "adults/a"}
   {:title "Beauty and the Beast" :video-files ["Beauty and the Beast.mp4"] :path "kids"}
   {:title "Blade Runner" :video-files ["Blade Runner.mp4"] :path "adults/b"}
   {:title "The 39 Steps" :video-files ["The 39 Steps.mp4"] :path "adults/#"}
   {:title "Boy" :video-files ["Boy.mp4"] :path "adults/b"}
   {:title "Crash [1996]" :video-files ["Crash.mp4"] :path "adults/c"}
   {:title "Crash (Cronenberg)" :video-files ["Crash.mp4"] :path "adults/c"}])

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn rand-movie []
  (let [title (rand-str 12)
        letter (str/lower-case (first title))]
    {:title title :video-files [(str title ".mp4")] :path (str "adults/" letter)}))

(def new-test-movie
  {:title "Mulan" :video-files ["Mulan.mp4"] :path "adults/m"})

(defn register-users []
  (client/register client "mike" "mike!")
  (client/register client "abby" "abby!"))

(defn sim-many []
  (db/reset db)
  (register-users)
  (storage/mock-movie-dirs! test-dir (repeatedly 200 rand-movie))
  (core/seed-movies! deps))

(defn sim-1 []
  (db/reset db)
  (register-users)
  (storage/mock-movie-dirs! test-dir test-movies)
  (core/sync-movies! deps))

(defn sim-2 []
  (storage/mock-movie-dir! test-dir new-test-movie)
  (core/sync-movies! deps))

(defn sim-3 []
  (core/sync-movies! deps))

(defn truncate [value places]
  (let [factor (.pow (BigInteger. "10") places)]
    (double (/ (int (* factor value)) factor))))

(defn rand-rating []
  (truncate (rand 10) 1))

(defn rand-rate-movies! [db]
  (doseq [movie (repo/list-movies db)]
    (repo/rate-movie! db (:uuid movie) "mike" (rand-rating))))

(comment
  ;; tmdb
  (tmdb/get-config tmdb)
  (-> (tmdb/get-movie tmdb 185) :body keys set)

  (->> (tmdb/search-movies tmdb "A Clockwork Orange" {:page 1}))

  (->> (tmdb/search-people tmdb "Kubrick"))

  (tmdb/search-people tmdb "Kubrick")

  (tmdb/search-people tmdb "Kubrick" {:page 1})

  (tmdb/get-person tmdb 240)

  (tmdb/get-movie tmdb 10020)

  ;; db
  (db/migrate db)
  (db/rollback db)
  (db/collect-tables db)
  (db/migrations db)
  (db/reset db)

  ;; storage
  (storage/mock-movie-dirs! test-dir test-movies)
  (storage/mock-movie! test-dir new-test-movie)
  (storage/populate-movie-metadata! test-dir)

  (storage/read-root-dir "movies/adults")

  ;; repo
  (repo/get-account-with-password db {:email "mike"})
  (repo/get-account db {:email "mike"})
  (repo/clear-movies! db)
  (repo/insert-movies! db (storage/read-movies-dir test-dir))
  (repo/list-movies db)

  (def akira (:uuid (repo/get-movie db {:title "Akira"})))

  (repo/get-movie db {:uuid akira})
  (repo/get-movie-id db akira)

  (repo/rate-movie! db akira 3.5)

  (repo/list-movies db)

  (repo/get-account-movie db "mike" {:uuid "61fdf65c-a36a-40cf-b24c-6c8d43c1bdc9"})

  (repo/get-account-movie db "mike" )

  ;; client
  (client/get-accounts client)
  (client/list-movies client)
  (client/sync-movies! client (storage/read-movies-dir test-dir))

  ;; core
  (core/sync-movies! deps)
  (core/list-movies deps)

  )


(def prod-db
  (jdbc/get-datasource (config/get-env-var "JDBC_DATABASE_URL")))

(def prod-cli-config (cli-config/config {:env "prod" :password (config/get-env-var "ADMIN_PASSWORD")}))

(def prod-deps (cli-config/deps prod-cli-config))

(comment

  (page-range 3 5)


  (backend-config/config)

  (core/sync-movies! prod-deps)
  (repo/clear-movies! prod-db)

  (db/reset prod-db)

  (storage/category-path "movies/kids" "kids")
  (jdbc/execute! prod-db ["SELECT * FROM movie"])

  )
