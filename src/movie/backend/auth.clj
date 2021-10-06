(ns movie.backend.auth
  (:require [com.stuartsierra.component :as component]
            [buddy.auth :as auth]
            [buddy.auth.backends :as auth-backends]
            [buddy.auth.middleware :as auth-middleware]
            [buddy.hashers :as auth-hashers]
            [buddy.sign.jwt :as auth-jwt]
            [movie.backend.repo :as repo]))

(def ^:private alg :hs512)

(defn- generate-token* [{:keys [admin-password db secret]} email password]
  (if-let [stored-account (if (= email "admin")
                            {:email "admin" :password admin-password}
                            (repo/get-account-with-password db {:email email}))]
    (if (:valid (auth-hashers/verify password (:password stored-account)))
      (let [account (dissoc stored-account :password)
            token (auth-jwt/sign account secret {:alg alg})]
        {:status "generated" :token token :account account})
      {:status "invalid-password"})
    {:status "missing-account"}))

(defn- register-account* [{:keys [db]} email password]
  (if (repo/get-account db {:email email})
    {:status "email-taken"}
    (let [hashed-password (auth-hashers/derive password)
          template {:email email :password hashed-password}]
      (repo/insert-account! db template)
      {:status "registered"})))

(def authenticated? auth/authenticated?)

(defprotocol IAuthSystem
  (middleware [this])
  (generate-token [this email password])
  (register-account [this email password]))

(defrecord AuthSystem [db admin-password secret]
  IAuthSystem
  (middleware [_]
    (let [token-backend (auth-backends/jws {:secret secret :options {:alg alg}})]
      (fn [handler] (auth-middleware/wrap-authentication handler token-backend))))
  (generate-token [this email password]
    (generate-token* this email password))
  (register-account [this email password]
    (register-account* this email password)))

(defn new-instance [{:keys [admin-password secret]}]
  (component/using
   (map->AuthSystem {:admin-password admin-password :secret secret})
   [:db]))
