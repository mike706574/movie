(ns movie.users.atomic
  (:require [movie.users :as users :refer [user-manager]]
            [buddy.hashers :as hashers])
  (:import [movie.users UserManager]))

(defn ^:private find-by-username
  [users username]
  (when-let [user (first (filter (fn [[user-id user]] (= (:movie/username user) username)) @users))]
    (val user)))

(defrecord AtomicUserManager [counter users]
  UserManager
  (add! [this user]
    (swap! users assoc (str (swap! counter inc))
           (update user :movie/password hashers/encrypt))
    (dissoc user :movie/password))

  (authenticate [this {:keys [:movie/username :movie/password]}]
    (when-let [user (find-by-username users username)]
      (when (hashers/check password (:movie/password user))
        (dissoc user :movie/password)))))

(defmethod user-manager :atomic
  [config]
  (let [user-manager (AtomicUserManager. (atom 0) (atom {}))]
    (when-let [users (:movie/users config)]
      (doseq [[username password] users]
        (users/add! user-manager {:movie/username username
                                  :movie/password password})))
    user-manager))
