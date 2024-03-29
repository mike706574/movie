(ns user
  (:require [aleph.http :as http]
            [buddy.hashers :as auth-hashers]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [movie.common.client :as client]
            [movie.common.config :as config]
            [movie.common.omdb :as omdb]
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
(def cli-deps (cli-config/deps cli-config))

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
  (core/seed-movies! cli-deps))

(defn sim-1 []
  (db/reset db)
  (register-users)
  (storage/mock-movie-dirs! test-dir test-movies)
  (core/sync-movies! cli-deps))

(defn sim-2 []
  (storage/mock-movie-dir! test-dir new-test-movie)
  (core/sync-movies! cli-deps))

(defn sim-3 []
  (core/sync-movies! cli-deps))

(defn truncate [value places]
  (let [factor (.pow (BigInteger. "10") places)]
    (double (/ (int (* factor value)) factor))))

(defn rand-rating []
  (truncate (rand 10) 1))

(defn rand-rate-movies! [db]
  (doseq [movie (repo/list-movies db)]
    (repo/rate-movie! db (:uuid movie) "mike" (rand-rating))))

(defn value-present? [value]
  (and (not (str/blank? value)) (not= value "N/A")))

(defn augment-movie [deps movie]
  (let [{:keys [omdb-client]} deps
        {:keys [uuid imdb-id]} movie]
    (let [{metascore :Metascore
           imdb-rating :imdbRating
           imdb-votes :imdbVotes} (:body (omdb/get-movie omdb-client imdb-id))]
      (merge movie
             {:metascore (when (value-present? metascore) (Integer/parseInt metascore))
              :imdb-rating (when (value-present? imdb-rating) (Double/parseDouble imdb-rating))
              :imdb-votes (when (value-present? imdb-votes)
                            (Integer/parseInt (str/replace imdb-votes #"," "")))}))))

(defn augment-movies! [{db :db :as deps}]
  (let [{db :db} deps
        stored-movies (repo/list-movies db)
        augmented-movies (map
                          (fn [movie]
                            (println "Processing movie" (:uuid movie))
                            (augment-movie deps movie))
                          stored-movies)]
    (println "Updating" (count augmented-movies) "movies")
    (repo/update-movies! db repo/extra-movie-fields augmented-movies)))

(comment
  (def omdb-client (omdb/new-client {:type "api"
                                     :url "http://www.omdbapi.com/"
                                     :key "5547242"}))

  (def prod-deps {:db prod-db :omdb-client omdb-client})

  (def dev-deps {:db db :omdb-client omdb-client})

  (repo/update-movies! prod-db repo/extra-movie-fields z)

  (augment-movies! dev-deps)

  (repo/list-account-movies prod-db "mike706574@gmail.com")
  (augment-movies! deps)

  (repo/update-movies! prod-db repo/extra-movie-fields [(augment-movie deps (repo/get-movie prod-db {:title "Akira"}))])

  ;; tmdb
  (tmdb/get-config tmdb)
  (-> (tmdb/get-movie tmdb 185) :body keys set)

  (->> (tmdb/search-movies tmdb "Le Cercle Rouge")
       :body)

  (->> (tmdb/search-people tmdb "Kubrick"))

  (tmdb/search-people tmdb "Kubrick")

  (tmdb/search-people tmdb "Kubrick" {:page 1})

  (tmdb/get-person tmdb 240)

  (tmdb/get-movie tmdb 10020)


  ;; db
  (db/migrate db)
  (db/rollback db)
  (db/list-views db)
  (db/collect-tables db)
  (db/migrations prod-db)
  (db/reset db)
  (db/get-view-definition db "movie_view")

  (db/list-columns db)
  (jdbc/execute! db ["SELECT * FROM schema_migrations"])

  ;; storage
  (storage/mock-movie-dirs! test-dir test-movies)
  (storage/mock-movie! test-dir new-test-movie)
  (storage/populate-movie-metadata! test-dir)

  (storage/read-root-dir "movies/adults")

  ;; repo
  (repo/list-movies prod-db)
  (repo/list-account-movies db "mike")
  (repo/get-account-with-password db {:email "mike"})
  (repo/get-account db {:email "mike"})
  (repo/clear-movies! db)
  (repo/insert-movies! db (storage/read-movies-dir test-dir))
  (repo/list-movies db)

  (def akira (:uuid (repo/get-movie prod-db {:title "Akira"})))

  (repo/get-movie db {:uuid akira})
  (repo/get-movie-id db akira)

  (repo/rate-movie! db akira 3.5)

  (repo/list-movies db)

  (repo/get-account-movie db "mike" {:uuid "61fdf65c-a36a-40cf-b24c-6c8d43c1bdc9"})

  (repo/get-account-movie db "mike" )


  (omdb/get-movie omdb-client "tt0101414")


  (let [movies (repo/list-movies db)
        params (mapv
                (fn [{:keys [uuid imdb-id]}]
                  (let [{metascore :Metascore
                         imdb-rating :imdbRating
                         imdb-votes :imdbVotes} (:body (omdb/get-movie omdb-client imdb-id))]
                    {:uuid uuid
                     :metascore metascore
                     :imdb-rating imdb-rating
                     :imdb-votes imdb-votes}))
                movies)]
    (jdbc/execute-batch! db "UPDATE movie SET imdb_rating = ? WHERE movie_id = ?" params {})
    params
    )

  (class (dec ))

  (def movies (repo/list-account-movies prod-db "mike706574@gmail.com"))


  (doseq [{:keys [uuid watched rating]} (->> movies
                                             (filter (complement :owned)))]
    (repo/update-account-movie! prod-db uuid "mike706574@gmail.com" {:owned true
                                                                     :watched watched
                                                                     :rating rating}))

  ;; client
  (client/get-accounts client)
  (client/list-movies client)
  (client/sync-movies! client (storage/read-movies-dir test-dir))

  ;; core
  (core/sync-movies! cli-deps)
  (core/list-movies cli-deps)

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

  (db/migrate prod-db)

  (storage/category-path "movies/kids" "kids")
  (jdbc/execute! prod-db ["SELECT * FROM movie"])

  )
