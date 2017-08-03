(ns movie.movies
  (:require [clojure.spec.alpha :as s]))

(s/def :movie/username string?)
(s/def :movie/password string?)
(s/def :movie/credentials (s/keys :req [:movie/username :movie/password]))

(defprotocol MovieManager
  "Abstraction around movie storage."
  (get-movies [this] "Gets all movies.")
  (add-movie! [this movie] "Adds a movie."))

(s/def :movie/movie-manager (partial satisfies? MovieManager))

(s/fdef add!
  :args (s/cat :movie-manager :movie/movie-manager
               :credentials :movie/credentials)
  :ret :movie/credentials)

(defmulti movie-manager :movie/movie-manager-type)

(defmethod movie-manager :default
  [{movie-manager-type :movie/movie-manager-type}]
  (throw (ex-info (str "Invalid movie manager type: " (name movie-manager-type))
                  {:movie-manager-type movie-manager-type})))
