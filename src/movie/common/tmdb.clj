(ns movie.common.tmdb
  (:require [aleph.http :as http]
            [clojure.string :as str]
            [movie.common.json :as json]
            [movie.common.util :as util]
            [taoensso.timbre :as log]))

(defn- retry-statuses
  [statuses]
  (fn retry? [response]
    (contains? (set statuses) (:status response))))

(defn- get-request
  [{:keys [url query-params retry-options]}]
  (let [{:keys [status body]} (util/with-retry
                                (fn execute-request []
                                  @(http/get url
                                             {:query-params query-params
                                              :headers {"Content-Type" "application/json;charset=utf8"}
                                              :throw-exceptions? false}))
                                (retry-statuses #{429})
                                (fn next-wait [wait] (+ wait 100))
                                retry-options)]
    (case status
      200 {:status :ok :body (json/read-value body)}
      429 {:status :retry-exhaustion}
      404 {:status :not-found}
      {:status :error :body (slurp body)})))

(defprotocol TmdbClient
  (get-config [this])
  (get-movie [this id])
  (search-movies [this query]))

(defrecord ApiTmdbClient [url key retry-options]
  TmdbClient
  (get-config [this]
    (let [url (str url "/configuration")]
      (get-request {:url url
                    :query-params {"api_key" key}
                    :retry-options retry-options})))

  (get-movie [this id]
    (let [url (str url "/movie/" id)]
      (log/debug (str "Getting movie with identifier \"" id " from " url "."))
      (get-request {:url url
                    :query-params {"api_key" key}
                    :retry-options retry-options})))

  (search-movies [this query]
    (let [url (str url "/search/movie")]
      (get-request {:url url
                    :query-params {"query" query "api_key" key}
                    :retry-options retry-options}))))

(defn client
  [config]
  (map->ApiTmdbClient config))
