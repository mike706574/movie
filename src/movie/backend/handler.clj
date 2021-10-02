(ns movie.backend.handler
  (:require [buddy.auth :as auth]
            [buddy.auth.backends :as auth-backends]
            [buddy.auth.middleware :as auth-middleware]
            [buddy.hashers :as auth-hashers]
            [buddy.sign.jwt :as auth-jwt]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [movie.backend.repo :as repo]
            [movie.common.json :as json]
            [movie.common.tmdb :as tmdb]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.util.response :as resp]
            [taoensso.timbre :as log]))

(def secret "secret")

(def alg :hs512)
(def sign #(auth-jwt/sign % secret {:alg alg}))
(def unsign #(auth-jwt/unsign % secret {:alg alg}))

(def token-backend
  (auth-backends/jws {:secret secret :options {:alg alg}}))

(defn auth [handler]
  (auth-middleware/wrap-authentication handler token-backend))

(defn auth-required [handler]
  (fn [req]
    (if (auth/authenticated? req)
      (handler req)
      {:status 401 :body {:error "Not authorized"}})))

(defn admin-required [handler]
  (fn [req]
    (if (= (get-in req [:identity :email]) "admin")
      (handler req)
      {:status 403 :body {:error "Must be an admin"}})))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri method] :as request}]
    (if (str/starts-with? uri "/js/")
      (handler request)
      (let [label (str method " \"" uri "\"")]
        (try
          (log/info label)
          (let [{:keys [status] :as response} (handler request)]
            (log/info (str label " -> " status))
            response)
          (catch Exception e
            (log/error e label)
            {:status 500
             :headers {"Content-Type" "application/json"}
             :body (json/write-value-as-string {:error (ex-message e)})}))))))

(defn check-account [db admin-password email password]
  (if-let [account (if (= email "admin")
                     {:email "admin" :password admin-password}
                     (repo/get-account db {:email email}))]
    (if (:valid (auth-hashers/verify password (:password account)))
      {:account (dissoc account :password)}
      {:error "invalid-password"})
    {:error "missing-account"}))

(defn routes
  [{:keys [admin-password db tmdb]}]
  [["/" {:get {:parameters {}
               :responses  {200 {:body any?}}
               :handler (fn [_] (resp/resource-response "public/index.html"))}}]

   ["/api" {:middleware [auth]}
    ["/accounts" {:get {:parameters {}
                        :responses {200 {:body any?}}
                        :handler (fn []
                                   {:status 200
                                    :body (repo/list-accounts db)})}

                  :post {:parameters {:body {:email string? :password string?}}
                         :responses {200 {:body any?}}
                         :handler (fn [{{{:keys [email password]} :body :as params} :parameters :as req}]
                                    (if-let [account (repo/get-account db {:email email})]
                                      {:status 400 :body {:error "email-taken"}}
                                      (let [hashed-password (auth-hashers/derive password)
                                            template {:email email :password hashed-password}]
                                        (repo/insert-account! db template)
                                        {:status 200 :body {:email email}})))}}]

    ["/tokens" {:post {:parameters {:body {:email string? :password string?}}
                       :responses {200 {:body any?}}
                       :handler (fn [{{{:keys [email password]} :body :as params} :parameters :as req}]
                                  (let [{:keys [error account]} (check-account db admin-password email password)]
                                    (if error
                                      {:status 401 :body {:error error}}
                                      (let [token (sign account)]
                                        {:status 200 :body (assoc account :token token)}))))}}]

    ["/movies" {:get {:parameters {}
                      :responses {200 {:body any?}}
                      :handler (fn [{identity :identity}]
                                 {:status 200
                                  :body (if identity
                                          (repo/list-account-movies db (:email identity))
                                          (repo/list-movies db))})}

                :post {:middleware [auth-required admin-required]
                       :parameters {:body any?}
                       :responses {200 {:body any?}}
                       :handler (fn [{{movies :body} :parameters identity :identity}]
                                  (log/debug "Syncing movies." {:identity identity})
                                  (let [result (repo/sync-movies! db movies)]
                                    {:status 200 :body result}))}}]

    ["/movies/:uuid" {:get {:parameters {:path {:uuid string?}}
                            :responses {200 {:body any?}}
                            :handler (fn [{{{uuid :uuid} :path} :parameters identity :identity}]
                                       (if-let [movie (if identity
                                                        (repo/get-account-movie db (:email identity) {:uuid uuid})
                                                        (repo/get-movie db {:uuid uuid}))]
                                         {:status 200 :body movie}
                                         {:status 404 :body {:uuid uuid}}))}

                      :post {:middleware [auth-required]
                             :parameters {:body any?
                                          :path {:uuid string?}}
                             :responses {200 {:body any?}}
                             :handler (fn [{{model :body {uuid :uuid} :path} :parameters
                                            {email :email} :identity}]
                                        (let [{rating :rating} model]
                                          (repo/rate-movie! db uuid email rating)
                                          {:status 200 :body {:rating rating}}))}}]

    ["/tmdb/search" {:get {:parameters {:query {:title string?}}}
                     :responses {200 {:body any?}}
                     :handler (fn [{{{:keys [title]} :query} :parameters}]
                                (let [results (tmdb/search-movies tmdb title)]
                                  {:status 200 :body results}))}]]

   ["/*" (ring/create-resource-handler)]])

(defn router [deps]
  (ring/router
   (routes deps)
   {:data {:coercion reitit.coercion.spec/coercion
           :muuntaja m/instance
           :middleware [muuntaja/format-middleware
                        parameters/parameters-middleware
                        rrc/coerce-request-middleware
                        rrc/coerce-response-middleware]}
    :conflicts (constantly nil)}))

(defn handler [deps]
  (ring/ring-handler
   (router deps)
   (ring/create-default-handler)
   {:middleware [wrap-logging]}))

(defprotocol IHandlerFactory
  (build [hf]))

(defrecord HandlerFactory [admin-password db movies tmdb]
  IHandlerFactory
  (build [this]
    (handler this))
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn factory [{:keys [admin-password]}]
  (component/using
   (map->HandlerFactory {:admin-password admin-password})
   [:db :tmdb]))
