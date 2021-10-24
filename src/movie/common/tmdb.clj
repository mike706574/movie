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
      {:status :error :code status :body (slurp body)})))

(defn- get-all-pages-request [req]
  (loop [page 1 all-results []]
    (let [{:keys [limit]} req
          {:keys [status body] :as response} (get-request (-> req
                                                              (assoc-in [:query-params "page"] page)
                                                              (dissoc :limit)))]
      (if (= status :ok)
        (let [{:keys [page total-pages results]} body
              all-results (into all-results results)]
          (cond
            (>= page total-pages) {:status :ok :body all-results}
            (and limit (<= limit (count all-results))) {:status :ok
                                                        :body (take limit all-results)}
            :else (recur (inc page) all-results)))
        response))))

(defn- get-page-request [req]
  (let [{:keys [page]} req
        {:keys [status body] :as resp} (get-request (-> req
                                               (assoc-in [:query-params "page"] page)
                                               (dissoc :page)))]
    (if (= status :ok)
      (let [{:keys [total-pages page total-results results]} body]
        {:status :ok
         :body results
         :total-pages total-pages
         :page page
         :total-results total-results})
      resp)))

(defn- get-paginated-request [req]
  (if (:page req)
    (get-page-request req)
    (get-all-pages-request req)))

(defprotocol TmdbClient
  (get-config [this])
  (get-movie [this id])
  (search-movies
    [this query]
    [this query options])
  (get-person [this id])
  (search-people
    [this query]
    [this query options]))

(defrecord ApiTmdbClient [url key retry-options]
  TmdbClient
  (get-config [_]
    (get-request {:url (str url "/configuration")
                  :query-params {"api_key" key}
                  :retry-options retry-options}))

  (get-movie [_ id]
    (get-request {:url (str url "/movie/" id)
                  :query-params {"api_key" key "append_to_response" "credits"}
                  :retry-options retry-options}))

  (search-movies [this query]
    (search-movies this query {}))

  (search-movies [_ query options]
    (get-paginated-request (merge options
                                  {:url (str url "/search/movie")
                                   :query-params {"query" query "api_key" key}
                                   :retry-options retry-options})))

  (get-person [_ id]
    (get-request {:url (str url "/person/" id)
                  :query-params {"api_key" key "append_to_response" "movie_credits"}
                  :retry-options retry-options}))

  (search-people [this query]
    (search-people this query {}))

  (search-people [_ query options]
    (get-paginated-request (merge options
                                  {:url (str url "/search/person")
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
     :body []})

  (search-movies [_ query options]
    ;; TODO
    {:status :ok
     :query query
     :body []}))

(defn new-client
  [config]
  (case (or (:type config) "api")
    "api" (map->ApiTmdbClient config)
    "dummy" (map->DummyTmdbClient config)))
