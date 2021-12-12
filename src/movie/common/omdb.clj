(ns movie.common.omdb
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

(defprotocol OmdbClient
  (get-config [this])
  (get-movie [this id])
  (search-movies
    [this query]
    [this query options])
  (get-person [this id])
  (search-people
    [this query]
    [this query options]))

(defrecord ApiOmdbClient [url key retry-options]
  OmdbClient
  (get-movie [_ id]
    (get-request {:url (str url)
                  :query-params {"apikey" key "i" id}
                  :retry-options retry-options})))

(defrecord DummyOmdbClient []
  OmdbClient
  (get-movie [_ id]
    ;; TODO
    {:status :ok
     :id id
     :body {}}))

(defn new-client
  [config]
  (case (or (:type config) "api")
    "api" (map->ApiOmdbClient config)
    "dummy" (map->DummyOmdbClient config)))
