(ns movie.storage.atomic
  (:require [movie.storage :refer [movie-storage]])
  (:import [movie.storage MovieStorage]))

(defrecord AtomicMovieStorage [counter movies]
  MovieStorage
  (get-movies [this]
    @movies)

  (add-movie! [this movie]
    (swap! movies assoc (str (swap! counter inc)) movies)))

(defmethod movie-storage :atomic
  [config]
  (AtomicMovieStorage. (atom 0) (atom {})))
