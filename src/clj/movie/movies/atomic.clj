(ns movie.movies.atomic
  (:require [movie.movies :as movies :refer [movie-manager]])
  (:import [movie.movies MovieManager]))

(defrecord AtomicMovieManager [counter movies]
  MovieManager
  (get-movies [this]
    @movies)

  (add-movie! [this movie]
    (swap! movies assoc (str (swap! counter inc)) movies)))

(defmethod movie-manager :atomic
  [config]
  (AtomicMovieManager. (atom 0) (atom {})))
