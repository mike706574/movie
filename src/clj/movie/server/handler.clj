(ns movie.server.handler
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [movie.server.api.handler :as api-handler]))

(defprotocol HandlerFactory
  "Builds a request handler."
  (handler [this]))

(defrecord BottleHandlerFactory [authenticator
                                 conn-manager
                                 event-bus
                                 user-manager
                                 movie-searcher
                                 websocket-content-type]
  HandlerFactory
  (handler [this]
    (api-handler/handler this)))

(defn factory
  [{:keys [:movie/websocket-content-type]}]
  (component/using
   (map->BottleHandlerFactory {:websocket-content-type websocket-content-type})
   [:authenticator :conn-manager :event-bus :movie-searcher :user-manager]))
