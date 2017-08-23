(ns movie.server.api.routes
  (:require [movie.users :as users]
            [movie.server.api.websocket :as websocket]
            [movie.server.authentication :as auth]
            [movie.search :as search]
            [movie.storage :as storage]
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

(def parse-int #(Integer/parseInt %))

(defn status-code
  [status]
  (case status
    :ok 200
    :invalid-args 400
    500))

(defn search-moviedb
  [{:keys [movie-searcher]} request]
  (handle-exceptions request
    (or (unsupported-media-type request)
        (let [{:strs [title page]} (:query-params request)
              {:keys [status body]} (search/search movie-searcher title page)]
          (body-response (status-code status) request body)))))

(s/def :movie/patch-type #{"watched" "unwatched"})
(s/def :movie/id integer?)
(s/def :movie/username string?)
(s/def :movie/patch (s/keys :req-un [:movie/patch-type
                                     :movie/id
                                     :movie/username]))

(defn update-movie!
  [{:keys [movie-storage]} request]
  (handle-exceptions request
    (let [id (get (:params request) "id")]
      (with-body [patch :movie/patch request]
        (let [{:keys [patch-type id username]} patch
              f (case patch-type
                  "watched" storage/watched!
                  "unwatched" storage/unwatched!)
              body (f movie-storage id username)]
          (body-response 200 request body))))))

(defn add-movie!
  [{:keys [movie-storage]} request]
  (handle-exceptions request
    (with-body [movie :movie/movie request]
      ;; TODO
      (let [added-movie (storage/add-movie! movie-storage movie)]
        (body-response 200 request added-movie)))))

(defn get-movies
  [{:keys [movie-storage]} request]
  (handle-exceptions request
    (or (unsupported-media-type request)
        (let [{:strs [letter page]} (:query-params request)
              response (log/spy (cond
                                  (and letter page)
                                  (storage/get-page-by-letter movie-storage letter (parse-int page))
                                  letter (storage/get-movies-by-letter movie-storage letter)
                                  page (storage/get-page movie-storage (parse-int page))
                                  :else (do (println "HERE") (storage/get-movies movie-storage))))]
          (body-response (status-code (:status response)) request response)))))

(defn routes
  [{:keys [user-manager authenticator] :as deps}]
  (letfn [(unauthenticated [request]
            (when-not (auth/authenticated? authenticator request)
              {:status 401}))]
    (compojure/routes
     (GET "/moviedb/movies" request
          (search-moviedb deps request))

     (GET "/api/movies" request
          (get-movies deps request))

     (PATCH "/api/movies/:id" request
            (update-movie! deps request))

     (POST "/api/movies" request
           (add-movie! deps request))

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
