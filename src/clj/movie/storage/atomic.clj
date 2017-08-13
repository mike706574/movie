(ns movie.storage.atomic
  (:require [movie.storage :refer [movie-storage]])
  (:import [movie.storage MovieStorage]))

(defrecord AtomicMovieStorage [counter movies]
  MovieStorage
  (get-movie [this id]
    (get @movies id))

  (get-movies [this]
    (map (fn [[k v]] (assoc v :id k)) @movies))

  (add-movie! [this movie]
    (let [id (str (swap! counter inc))]
      (swap! movies assoc id movie)
      (assoc (get @movies id) :id id))))

(defmethod movie-storage :atomic
  [config]
  (AtomicMovieStorage. (atom 0) (atom {})))
