(ns movie.cli.config
  (:require [movie.common.client :as client]
            [movie.common.config :as config]
            [movie.common.tmdb :as tmdb]))

(defn config
  ([]
   (config {:env (config/get-env)
            :password (config/get-env-var "")}))

  ([{:keys [env password]}]
   (merge
    {:tmdb {:url "https://api.themoviedb.org/3"
            :key "7197608cef1572f5f9e1c5b184854484"
            :retry-options {:initial-wait 0
                            :max-attempts 3}}}
    (case env
      "dev" {:path "movies"
             :client {:url "http://localhost:7600"
                      :email "admin"
                      :password password}}
      "prod" {:path "movies"
              :client {:url "https://movie-mike.herokuapp.com"
                       :email "admin"
                       :password password}}))))

(defn deps
  [config]
  {:path (:path config)
   :client (client/client (:client config))
   :tmdb (tmdb/client (:tmdb config))})
