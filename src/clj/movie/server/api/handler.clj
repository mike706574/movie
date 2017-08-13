(ns movie.server.api.handler
  (:require [movie.server.api.routes :as api-routes]
            [clojure.string :as str]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [wrap-defaults
                                              api-defaults]]
            [ring.middleware.params :refer [wrap-params]]
            [taoensso.timbre :as log]))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri request-method] :as request}]
    (let [label (str (-> request-method name str/upper-case) " \"" uri "\"")]
      (try
        (log/debug label)
        (let [{:keys [status] :as response} (handler request)]
          (log/debug (str label " -> " status))
          response)
        (catch Exception e
          (log/error e label)
          {:status 500})))))

(defn handler
  [deps]
  (-> (api-routes/routes deps)
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :patch :delete])
      (wrap-params)
      (wrap-defaults api-defaults)
      (wrap-logging)))
