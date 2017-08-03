(ns movie.client
  (:require [aleph.http :as http]
            [manifold.stream :as s]
            [boomerang.message :as message]
            [movie.users :as users]))

(def content-type "application/transit+json")

(defn parse
  [response]
  (let [response-content-type (get-in response [:headers "content-type"])]
    (if (and (contains? response :body) (= response-content-type content-type))
      (update response :body (comp (partial message/decode content-type)))
      response)))

(defn get-request
  [url]
  (parse @(http/get url
                    {:headers {"Content-Type" content-type
                               "Accept" content-type}
                     :throw-exceptions false})))

(defn post-request
  [url body]
  (parse @(http/post url
                     {:headers {"Content-Type" content-type
                                "Accept" content-type}
                      :body (message/encode content-type body)
                      :throw-exceptions false})))

(defn add-user!
  [system username password]
  (users/add! (:user-manager system) {:movie/username username
                                      :movie/password password}))

(defn http-url [host] (str "http://" host))

(defprotocol Client
  (authenticate [this credentials]))

(defrecord ServiceClient [host content-type token]
  Client
  (authenticate [this credentials]
    (let [response @(http/post (str (http-url host) "/api/tokens")
                               {:headers {"Content-Type" content-type
                                          "Accept" "text/plain"}
                                :body (message/encode content-type credentials)
                                :throw-exceptions false})]
      (when (= (:status response) 201)
        (assoc this :token (-> response :body slurp))))))

(defn client
  [{:keys [host content-type]}]
  (map->ServiceClient {:host host
                       :content-type content-type}))
