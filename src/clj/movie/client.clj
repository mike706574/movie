(ns movie.client
  (:require [aleph.http :as http]
            [boomerang.message :as message]
            [manifold.stream :as s]
            [movie.users :as users]))

(def content-type "application/json")

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
  (authenticate [this credentials])
  (get-movies [this params])
  (update-movie! [this id changes])
  (add-movie! [this movie])
  (moviedb-search [this title page]))

(defrecord ServiceClient [host content-type token]
  Client
  (authenticate [this credentials]
    (let [response @(http/post (str (http-url host) "/api/tokens")
                               {:headers {"Content-Type" content-type
                                          "Accept" "text/plain"}
                                :body (message/encode content-type credentials)
                                :throw-exceptions false})]
      (when (= (:status response) 201)
        (assoc this :token (-> response :body slurp)))))

  (get-movies [this params]
    (parse @(http/get (str (http-url host) "/api/movies")
                      {:headers {"Accept" "application/json"}
                       :query-params params
                       :throw-exceptions false})))

  (update-movie! [this id changes]
    (parse @(http/patch (str (http-url host) (str "/api/movies/" (name id)))
                        {:headers {"Accept" "application/json"}
                         :body (message/encode content-type changes)
                         :throw-exceptions false})))

  (add-movie! [this movie]
    (parse @(http/post (str (http-url host) (str "/api/movies"))
                       {:headers {"Accept" "application/json"}
                        :body (message/encode content-type movie)
                        :throw-exceptions false})))

  (moviedb-search [this title page]
    (parse @(http/get (str (http-url host) "/moviedb/movies")
                      {:headers {"Accept" "application/json"}
                       :query-params {:title title
                                      :page page}
                       :throw-exceptions false}))))

(defn client
  [{:keys [host content-type]}]
  (map->ServiceClient {:host host
                       :content-type content-type}))
