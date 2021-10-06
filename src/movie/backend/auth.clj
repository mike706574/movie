(ns movie.backend.auth
  (:require [buddy.auth :as auth]
            [buddy.auth.backends :as auth-backends]
            [buddy.hashers :as auth-hashers]
            [buddy.sign.jwt :as auth-jwt]
            [movie.backend.repo :as repo]))

;; TODO: Load from environment
(def secret "secret")

(def alg :hs512)
(def sign #(auth-jwt/sign % secret {:alg alg}))
(def unsign #(auth-jwt/unsign % secret {:alg alg}))

(def token-backend
  (auth-backends/jws {:secret secret :options {:alg alg}}))

(defn generate-token [db admin-password email password]
  (if-let [stored-account (if (= email "admin")
                            {:email "admin" :password admin-password}
                            (repo/get-account db {:email email}))]
    (if (:valid (auth-hashers/verify password (:password stored-account)))
      (let [account (dissoc stored-account :password)
            token (sign account)]
        {:status "generated" :token token :account account})
      {:status "invalid-password"})
    {:status "missing-account"}))

(defn register-account [db email password]
  (if-let [account (repo/get-account db {:email email})]
    {:status "email-taken"}
    (let [hashed-password (auth-hashers/derive password)
          template {:email email :password hashed-password}]
      (repo/insert-account! db template)
      {:status "registered"})))

(def authenticated? auth/authenticated?)
