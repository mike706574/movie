(ns movie.common.client
  (:require [aleph.http :as http]
            [movie.common.json :as json]))

(defn- get-request [{:keys [url query-params headers]}]
  (let [{:keys [status body]} @(http/get url {:query-params query-params
                                              :headers headers})]
    (if (= 200 status)
      (json/read-value body))))

(defn- post-request [{:keys [url body headers]}]
  (let [{:keys [status body]} @(http/post url {:body (json/write-value-as-string body)
                                               :headers (assoc headers "Content-Type" "application/json")})]
    (if (= 200 status)
      (json/read-value body))))

(defn- get-auth-headers [{:keys [url email password]}]
  (if email
    {}
    (let [token (:token (post-request {:url (str url "/login")
                                       :body {:email email :password password}}))]
      {"Authorization" (str "Token " token)})))

(defprotocol SystemClient
  (register [this email password])
  (list-movies [this])
  (sync-movies! [this movies]))

(defrecord ApiSystemClient [url email password]
  SystemClient
  (register [this new-email new-password]
    (post-request {:url (str url "/register")
                   :body {:email new-email :password new-password}}))

  (list-movies [_]
    (get-request {:url (str url "/api/movies")}))

  (sync-movies! [this movies]
    (post-request {:url (str url "/api/movies")
                   :body movies
                   :headers (get-auth-headers this)})))

(defn client
  [config]
  (map->ApiSystemClient config))
