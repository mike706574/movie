(ns movie.cli.config
  (:require [movie.common.client :as client]
            [movie.common.config :as config]
            [movie.common.tmdb :as tmdb]))

(defn config
  ([]
   (config {:env (config/get-env)
            :password (config/get-env-var "")}))

  ([{:keys [env password]}]
   {:sources [{:kind "root-dir" :path "movies/adults"}
              {:kind "category-dir" :path "movies/kids" :category "kids"}]
    :tmdb {:type "api"
           :url "https://api.themoviedb.org/3"
           :key "7197608cef1572f5f9e1c5b184854484"
           :retry-options {:initial-wait 0
                           :max-attempts 3}}
    :client (case env
              "dev" {:url "http://localhost:7600"
                     :email "admin"
                     :password password}
              "prod" {:url "https://movie-mike.herokuapp.com"
                      :email "admin"
                      :password password})}))

(defn deps
  [config]
  {:sources (:sources config)
   :client (client/new-client (:client config))
   :tmdb (tmdb/new-client (:tmdb config))})
