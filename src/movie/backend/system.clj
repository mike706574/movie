(ns movie.backend.system
  (:require [movie.backend.db :as db]
            [movie.backend.handler :as handler]
            [movie.common.tmdb :as tmdb]
            [movie.backend.service :as service]))

(defn system
  [config]
  {:db (db/new-db (:db config))
   :service (service/service config)
   :handler-factory (handler/factory config)
   :tmdb (tmdb/client (:tmdb config))})
