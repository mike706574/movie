(ns movie.client
  (:require [aleph.http :as http]
            [manifold.stream :as s]
            [boomerang.message :as message]
            [movie.users :as users]))

(def content-type "application/transit+json")

(defn receive!
  ([conn]
   (receive! conn 100))
  ([conn timeout]
   (let [out @(s/try-take! conn :drained timeout :timeout)]
     (if (contains? #{:drained :timeout} out) out (message/decode content-type out)))))

(defn flush!
  [conn]
  (loop [out :continue]
    (when (not= out :timeout)
      (recur @(s/try-take! conn :drained 10 :timeout)))))

(defn send!
  [conn message]
  (s/put! conn (message/encode content-type message)))

(defn parse
  [response]
  (let [response-content-type (get-in response [:headers "content-type"])]
    (println "RESPONSE:" response-content-type)
    (println "RESPONSEeawklj:" content-type)
    (if (and (contains? response :body) (= response-content-type content-type))
      (do (println "YES")
          (update response :body (comp (partial message/decode content-type))))
      (do (println "NO")
          response))))

(defn transit-get
  [url]
  (parse @(http/get url
                    {:headers {"Content-Type" content-type
                               "Accept" content-type}
                     :throw-exceptions false})))

(defn transit-post
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
(defn ws-url [host] (str "ws://" host))

(defn connect!
  ([host token]
   (connect! host token nil))
  ([host token category]
   (let [endpoint-url (str (ws-url host) "/api/websocket")
         url (if category
               (str endpoint-url "/" (name category))
               endpoint-url)
         url (str url "?token=" token)
         conn @(http/websocket-client url)]
     conn)))

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
