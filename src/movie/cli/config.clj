(ns movie.cli.config
  (:require [movie.common.client :as client]
            [movie.common.config :as config]
            [movie.common.tmdb :as tmdb]))

(defn config
  ([]
   (config {:env (config/get-env)}))

  ([{:keys [env]}]
   (merge
    {:tmdb {:url "https://api.themoviedb.org/3"
            :key "7197608cef1572f5f9e1c5b184854484"
            :retry-options {:initial-wait 0
                            :max-attempts 3}}}
    (case env
      "dev" {:client {:url "http://localhost:7600"}
             :path "movies"}
      "prod" {:client {:url "TODO"}
              :path "TODO"}))))

(defn deps
  [config]
  {:path (:path config)
   :client (client/client (:client config))
   :tmdb (tmdb/client (:tmdb config))})
