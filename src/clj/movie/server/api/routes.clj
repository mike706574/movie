(ns movie.server.api.routes
  (:require [movie.users :as users]
            [movie.server.api.websocket :as websocket]
            [movie.server.authentication :as auth]
            [boomerang.http :refer [with-body
                                    handle-exceptions
                                    body-response
                                    not-acceptable
                                    parsed-body
                                    unsupported-media-type]]
            [clj-time.core :as time]
            [clojure.walk :as walk]
            [clojure.spec.alpha :as s]
            [compojure.core :as compojure :refer [ANY DELETE GET PATCH POST PUT]]
            [compojure.route :as route]
            [taoensso.timbre :as log]))

(defn routes
  [{:keys [user-manager authenticator] :as deps}]
  (letfn [(unauthenticated [request]
            (when-not (auth/authenticated? authenticator request)
              {:status 401}))]
    (compojure/routes
     (POST "/api/tokens" request
           (try
             (or (not-acceptable request #{"text/plain"})
                 (with-body [credentials :movie/credentials request]
                   (if-let [user (users/authenticate user-manager credentials)]
                     {:status 201
                      :headers {"Content-Type" "text/plain"}
                      :body (auth/token authenticator (:movie/username credentials))}
                     {:status 401})))
             (catch Exception e
               (log/error e "An exception was thrown while attempting to generate a token.")
               {:status 500
                :headers {"Content-Type" "text/plain"}
                :body "An error occurred."})))
     (GET "/api/healthcheck" request
          {:status 200})
     (route/not-found {:status 404}))))
