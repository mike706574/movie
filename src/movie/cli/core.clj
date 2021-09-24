(ns movie.cli.core
  (:require [movie.common.storage :as storage]
            [movie.common.client :as client]))

(defn list-movies
  [{:keys [client]}]
  (client/list-movies client))

(defn sync-movies!
  [{:keys [path client]}]
  (let [movies (storage/populate-movie-metadata! path)]
    (client/sync-movies! client movies)))
