(ns movie.common.client
  (:require [aleph.http :as http]
            [movie.common.json :as json]))

(defn- get-request
  [{:keys [url query-params]}]
  (let [{:keys [status body]} @(http/get url {:query-params query-params
                                              :throw-exceptions? false})]
    (if (= 200 status)
      {:status :ok :body (json/read-value body)}
      {:status :error :code status :body body})))

(defn- post-request
  [{:keys [url body]}]
  (let [{:keys [status body]} @(http/post url {:body (json/write-value-as-string body)
                                               :headers {"Content-Type" "application/json"}
                                               :throw-exceptions? false})]
    (if (= 200 status)
      {:status :ok :body (json/read-value body)}
      {:status :error :code status :body body})))

(defprotocol SystemClient
  (list-movies [this])
  (sync-movies! [this movies]))

(defrecord ApiSystemClient [url]
  SystemClient
  (list-movies [_]
    (get-request {:url (str url "/api/movies")}))

  (sync-movies! [_ movies]
    (let [url (str url "/api/movies")]
      (post-request {:url url
                     :body movies}))))

(defn client
  [config]
  (map->ApiSystemClient config))
