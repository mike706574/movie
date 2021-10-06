(ns movie.backend.middleware
  (:require [buddy.auth.middleware :as auth-middleware]
            [clojure.string :as str]
            [movie.backend.auth :as auth]
            [movie.common.json :as json]
            [taoensso.timbre :as log]))

(defn auth-required [handler]
  (fn [req]
    (if (auth/authenticated? req)
      (handler req)
      {:status 401 :body {:error "Not authorized"}})))

(defn admin-required [handler]
  (fn [req]
    (if (= (get-in req [:identity :email]) "admin")
      (handler req)
      {:status 403 :body {:error "Must be an admin"}})))

(defn logging [handler]
  (fn [{:keys [uri method] :as request}]
    (if (str/starts-with? uri "/js/")
      (handler request)
      (let [label (str method " \"" uri "\"")]
        (try
          (log/info label)
          (let [{:keys [status] :as response} (handler request)]
            (log/info (str label " -> " status))
            response)
          (catch Exception e
            (log/error e label)
            {:status 500
             :headers {"Content-Type" "application/json"}
             :body (json/write-value-as-string {:error (ex-message e)})}))))))
