(ns movie.backend.handler
  (:require [com.stuartsierra.component :as component]
            [movie.backend.middleware :as mw]
            [movie.backend.routes :as routes]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.coercion.malli]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger-ui :as swagger-ui]))

(defn router [deps]
  (ring/router
   (routes/routes deps)
   {:data {:coercion reitit.coercion.malli/coercion
           :muuntaja m/instance
           :middleware [muuntaja/format-middleware
                        parameters/parameters-middleware
                        coercion/coerce-request-middleware
                        coercion/coerce-response-middleware]}}))

(defn handler [deps]
  (ring/ring-handler
   (router deps)
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/swagger-ui"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))
   {:middleware [mw/logging]}))

(defprotocol IHandlerFactory
  (build [this]))

(defrecord HandlerFactory [auth db tmdb]
  IHandlerFactory
  (build [this] (handler this))
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn new-factory [{:keys [admin-password]}]
  (component/using
   (map->HandlerFactory {:admin-password admin-password})
   [:auth :db :tmdb]))
