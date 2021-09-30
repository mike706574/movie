(ns movie.backend.handler
  (:require [buddy.auth :as auth]
            [buddy.auth.backends :as auth-backends]
            [buddy.auth.middleware :as auth-middleware]
            [buddy.sign.jwt :as auth-jwt]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [movie.backend.repo :as repo]
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
  (auth-backends/jws {:secret secret
                      :options {:alg alg}
                      :on-error (fn [req e]
                                  (.printStackTrace e))}))

(defn auth [handler]
  (auth-middleware/wrap-authentication handler token-backend))

(defn auth-required [handler]
  (fn [req]
    (if (auth/authenticated? req)
      (handler req)
      {:status 401 :body {:error "Not authorized"}})))

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
            {:status 500}))))))

(defn find-user [email password]
  (when (and (= email "mike706574@gmail.com") (= password "mike"))
    {:email email}))

(defn routes
  [{:keys [db tmdb]}]
  [["/" {:get {:parameters {}
               :responses  {200 {:body any?}}
               :handler (fn [_]
                          (resp/resource-response "public/index.html"))}}]

   ["/login" {:post {:parameters {:body {:email string? :password string?}}
                     :responses {200 {:body any?}}
                     :handler (fn [{{{:keys [email password]} :body :as params} :parameters :as req}]

                                (if-let [user (find-user email password)]
                                  (let [token (sign {:email email})]
                                    {:status 200 :body {:email email :token token}})
                                  {:status 400 :body {:message "User not found."}}))}}]

   ["/api" {:middleware [auth]}
    ["/movies" {:get {:parameters {}
                      :responses {200 {:body any?}}
                      :handler (fn [req]
                                 {:status 200 :body (repo/list-movies db)})}

                :post {:middleware [auth-required]
                       :parameters {:body any?}
                       :responses {200 {:body any?}}
                       :handler (fn [{{movies :body} :parameters}]
                                  (let [result (repo/sync-movies! db movies)]
                                    {:status 200 :body result}))}}]

    ["/movies/:uuid" {:get {:parameters {:path {:uuid string?}}
                            :responses {200 {:body any?}}
                            :handler (fn [{{{uuid :uuid} :path} :parameters :as req}]
                                       (if-let [movie (repo/get-movie db {:uuid uuid})]
                                         {:status 200 :body movie}
                                         {:status 404 :body {:uuid uuid}}))}

                      :post {:middleware [auth-required]
                             :parameters {:body any?
                                          :path {:uuid string?}}
                             :responses {200 {:body any?}}
                             :handler (fn [{{model :body {uuid :uuid} :path :as params} :parameters}]
                                        (let [{rating :rating} model]
                                          (repo/rate-movie! db uuid rating)
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

(defrecord HandlerFactory [db movies tmdb]
  IHandlerFactory
  (build [this]
    (handler this))
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn factory []
  (component/using
   (map->HandlerFactory {})
   [:db :tmdb]))
