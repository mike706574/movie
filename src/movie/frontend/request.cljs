(ns movie.frontend.request
  (:require [ajax.core :as ajax]
            [movie.frontend.storage :as storage]))

(defn auth-headers
  ([]
   (auth-headers (storage/get-account)))
  ([account]
   (if account
     {"Authorization" (str "Token " (:token account))}
     {})))

(def json-req-format (ajax/json-request-format {:keywords? true}))
(def json-resp-format (ajax/json-response-format {:keywords? true}))

(defn get-json-request [options]
  (merge {:method :get
          :response-format json-resp-format
          :on-failure [:handle-failure]}
         options
         {:headers (merge (:headers options) (auth-headers))}))

(defn post-json-request [options]
  (merge {:method :post
          :format json-req-format
          :response-format json-resp-format
          :on-failure [:handle-failure]}
         options
         {:headers (merge (:headers options) (auth-headers))}))
