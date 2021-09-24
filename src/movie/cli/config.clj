(ns movie.cli.config
  (:require [environ.core :as environ]
            [movie.common.client :as client]
            [movie.common.config :as config]))

(defn config
  ([]
   (config {:env (config/get-env)}))

  ([{:keys [env]}]
   (case env
     "dev" {:url "http://localhost:7600"
            :path "movies"}
     "prod" {:url "TODO"
             :path "TODO"})))

(defn deps
  [config]
  {:path (:path config)
   :client (client/client config)})
