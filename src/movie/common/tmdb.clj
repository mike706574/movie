(ns movie.common.tmdb
  (:require [aleph.http :as http]
            [clojure.pprint :as pprint]
            [movie.common.json :as json]
            [taoensso.timbre :as log]))

(defn- pretty [form] (with-out-str (pprint/pprint form)))

(defn- with-retry
  [operation retry? next-wait opts]
  (let [{:keys [initial-wait max-attempts] :or {initial-wait 0}} opts]
    (loop [i 1
           wait initial-wait]
      (let [output (operation)]
        (log/trace (str "Attempt " i " output: " (pretty output)))
        (if (retry? output)
          (if (> i max-attempts)
            (do (log/error (str "Failed after " max-attempts" attempts."))
                output)
            (do (log/warn (str "Attempt " i " of " max-attempts " failed. Sleeping for " wait " ms."))
                (Thread/sleep wait)
                (recur (inc i) (next-wait wait))))
          output)))))

(defn- retry-statuses
  [statuses]
  (fn retry? [response]
    (contains? (set statuses) (:status response))))

(defn- get-request
  [{:keys [url query-params retry-options]}]
  (let [{:keys [status body]} (with-retry
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

(defn- get-paginated-results [req]
  (loop [page 1 all-results []]
    (let [{:keys [status body] :as response} (get-request (assoc-in req [:query-params "page"] page))]
      (if (= status :ok)
        (let [{:keys [page total-pages results]} body
              all-results (into all-results results)]
          (if (= page total-pages)
            {:status :ok :body all-results}
            (recur (inc page) all-results)))
        response))))

(defprotocol TmdbClient
  (get-config [this])
  (get-movie [this id])
  (search-movies [this query]))

(defrecord ApiTmdbClient [url key retry-options]
  TmdbClient
  (get-config [_]
    (let [url (str url "/configuration")]
      (get-request {:url url
                    :query-params {"api_key" key}
                    :retry-options retry-options})))

  (get-movie [_ id]
    (let [url (str url "/movie/" id)]
      (get-request {:url url
                    :query-params {"api_key" key}
                    :retry-options retry-options})))

  (search-movies [_ query]
    (let [url (str url "/search/movie")]
      (get-paginated-results {:url url
                              :query-params {"query" query "api_key" key}
                              :retry-options retry-options}))))

(defrecord DummyTmdbClient []
  TmdbClient
  (get-config [_]
    ;; TODO
    {:status :ok
     :body {}})
  (get-movie [_ id]
    ;; TODO
    {:status :ok
     :id id
     :body {}})
  (search-movies [_ query]
    ;; TODO
    {:status :ok
     :query query
     :body []}))

(defn new-client
  [config]
  (case (or (:type config) "api")
    "api" (map->ApiTmdbClient config)
    "dummy" (map->DummyTmdbClient config)))
