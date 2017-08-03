(ns movie.storage
  (:require [clojure.spec.alpha :as s]))

(s/def :movie/username string?)
(s/def :movie/password string?)
(s/def :movie/credentials (s/keys :req [:movie/username :movie/password]))

(defprotocol MovieManager
  "Abstraction around movie storage."
  (movies [this] "Gets all movies.")
  (add-movie! [this movie] "Adds a movie."))

(s/def :movie/movie-manager (partial satisfies? MovieManager))

(s/fdef add!
  :args (s/cat :movie-manager :movie/movie-manager
               :credentials :movie/credentials)
  :ret :movie/credentials)

(defmulti movie-manager :movie/movie-manager-type)

(defmethod movie-manager :default
  [{user-manager-type :movie/user-manager-type}]
  (throw (ex-info (str "Invalid user manager type: " (name user-manager-type))
                  {:user-manager-type user-manager-type})))
