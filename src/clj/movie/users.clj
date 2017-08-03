(ns movie.users
  (:require [clojure.spec.alpha :as s]))

(s/def :movie/username string?)
(s/def :movie/password string?)
(s/def :movie/credentials (s/keys :req [:movie/username :movie/password]))

(defprotocol UserManager
  "Abstraction around user storage and authentication."
  (add! [this user] "Adds a user.")
  (authenticate [this credentials] "Authenticates a user."))

(s/def :movie/user-manager (partial satisfies? UserManager))

(s/fdef add!
  :args (s/cat :user-manager :movie/user-manager
               :credentials :movie/credentials)
  :ret :movie/credentials)

(defmulti user-manager :movie/user-manager-type)

(defmethod user-manager :default
  [{user-manager-type :movie/user-manager-type}]
  (throw (ex-info (str "Invalid user manager type: " (name user-manager-type))
                  {:user-manager-type user-manager-type})))

#_(s/fdef user-manager
  :args (s/cat :config map?)
  :ret (partial satisfies? UserManager))
