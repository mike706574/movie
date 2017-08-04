(ns movie.moviedb-client
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [movie.util :as util]
            [taoensso.timbre :as log]))

(log/set-level! :debug)
(defn ^:private retry-statuses
  [statuses]
  (fn retry? [response]
    (contains? (set statuses) (:status response))))

(defn ^:private get-request
  [url query-params retry-options]
  (let [{:keys [status body]} (util/with-retry
                                (fn execute-request []
                                  (http/get url
                                            {:query-params query-params
                                             :headers {"Content-Type" "application/json;charset=utf8"}
                                             :throw-exceptions false}))
                                (retry-statuses #{429})
                                (fn next-wait [wait] (+ wait 100))
                                retry-options)]
    (case status
      200 {:status :ok :body (json/read-str body :key-fn keyword)}
      429 {:status :retry-exhaustion}
      404 {:status :not-found :body body}
      {:status :error :body body})))

(defprotocol MovieClient
  (get-config [this])
  (get-movie [this id])
  (search-movies [this query] [this query page]))

(defrecord TMDb3MovieClient [url api-key retry-options]
  MovieClient
  (get-config [this]
    (let [url (str url "/configuration")]
      (get-request url {"api_key" api-key} retry-options)))

  (get-movie [this id]
    (let [url (str url "/movie/" id)]
      (log/debug (str "Getting movie with identifier \"" id " from " url "."))
      (get-request url {"api_key" api-key} retry-options)))

  (search-movies
    [this query]
    (search-movies this query 1))

  (search-movies
    [this query page]
    (let [url (str url "/search/movie")]
      (get-request url {"query" query "page" page "api_key" api-key} retry-options))))

(defn client
  [{:keys [movie-api-url movie-api-key movie-api-retry-options]}]
  (map->TMDb3MovieClient {:url movie-api-url
                         :api-key movie-api-key
                         :retry-options movie-api-retry-options}))

(comment
  (def config {:movie-api-url "https://api.themoviedb.org/3"
               :movie-api-key "7197608cef1572f5f9e1c5b184854484"
               :movie-api-retry-options {:initial-wait 0
                                         :max-attempts 3}})

  (def c (movie-client config))

  (search-movies c "blue" 83)
  )
