(ns movie.storage
  (:require [clojure.spec.alpha :as s]))

(s/def :movie/username string?)
(s/def :movie/password string?)
(s/def :movie/credentials (s/keys :req [:movie/username :movie/password]))

(defprotocol MovieStorage
  "Abstraction around movie storage."
  (get-movies [this] "Gets all movies.")
  (add-movie! [this movie] "Adds a movie."))

(s/def :movie/movie-storage (partial satisfies? MovieStorage))

(defmulti movie-storage :movie/movie-storage-type)

(defmethod movie-storage :default
  [{movie-storage-type :movie/movie-storage-type}]
  (throw (ex-info (str "Invalid user manager type: " (name movie-storage-type))
                  {:movie-storage-type movie-storage-type})))
