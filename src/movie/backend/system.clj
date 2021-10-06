(ns movie.backend.system
  (:require [movie.backend.auth :as auth]
            [movie.backend.db :as db]
            [movie.backend.handler :as handler]
            [movie.common.tmdb :as tmdb]
            [movie.backend.service :as service]))

(defn system
  [config]
  {:auth (auth/new-instance (:auth config))
   :db (db/new-db (:db config))
   :handler-factory (handler/new-factory config)
   :service (service/new-service config)
   :tmdb (tmdb/new-client (:tmdb config))})
