(ns movie.backend.routes
  (:require [movie.backend.auth :as auth]
            [movie.backend.middleware :as mw]
            [movie.backend.repo :as repo]
            [movie.common.tmdb :as tmdb]
            [reitit.swagger :as swagger]
            [ring.util.response :as resp]
            [taoensso.timbre :as log]))

(defn routes
  [{:keys [admin-password db tmdb]}]
  [["/" {:get {:no-doc true
               :parameters {}
               :responses  {200 {:body any?}}
               :handler (fn [_] (resp/resource-response "public/index.html"))}}]

   ["/swagger.json" {:get {:no-doc true
                           :swagger {:info {:title "movie api"}}
                           :handler (swagger/create-swagger-handler)}}]

   ["/api" {:middleware [mw/auth]}
    ["/accounts" {:get {:parameters {}
                        :responses {200 {:body any?}}
                        :handler (fn []
                                   {:status 200
                                    :body (repo/list-accounts db)})}

                  :post {:parameters {:body {:email string? :password string?}}
                         :responses {200 {:body any?}}
                         :handler (fn [{{{:keys [email password]} :body :as params} :parameters :as req}]
                                    (let [{:keys [status]} (auth/register-account db email password)]
                                      (case status
                                        "registered" {:status 200 :body {:email email}}
                                        "email-taken" {:status 400 :body {:error "email-taken"}})))}}]

    ["/tokens" {:post {:parameters {:body {:email string? :password string?}}
                       :responses {200 {:body any?}}
                       :handler (fn [{{{:keys [email password]} :body :as params} :parameters :as req}]
                                  (let [{:keys [status account token]} (auth/generate-token db admin-password email password)]
                                    (if (= status "generated")
                                      {:status 200 :body {:account account :token token}}
                                      {:status 401 :body {:error status}})))}}]

    ["/movies" {:get {:parameters {}
                      :responses {200 {:body any?}}
                      :handler (fn [{identity :identity}]
                                 {:status 200
                                  :body (if identity
                                          (repo/list-account-movies db (:email identity))
                                          (repo/list-movies db))})}

                :post {:middleware [mw/auth-required mw/admin-required]
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

                      :post {:middleware [mw/auth-required]
                             :parameters {:body any?
                                          :path {:uuid string?}}
                             :responses {200 {:body any?}}
                             :handler (fn [{{model :body {uuid :uuid} :path} :parameters
                                            {email :email} :identity}]
                                        (let [{rating :rating} model]
                                          (repo/rate-movie! db uuid email rating)
                                          {:status 200 :body {:rating rating}}))}}]

    ["/tmdb/search" {:get {:parameters {:query {:title string?}}
                           :responses {200 {:body any?}}
                           :handler (fn [{{{:keys [title]} :query} :parameters}]
                                      (let [results (tmdb/search-movies tmdb title)]
                                        {:status 200 :body results}))}}]]])
