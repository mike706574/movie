(ns movie.server.system
  (:require [boomerang.message :as message]
            [manifold.bus :as bus]
            [movie.server.authentication :as auth]
            [movie.users :as users]
            [movie.util :as util]
            [movie.moviedb-client :as moviedb-client]
            [movie.search :as search]
            [movie.server.connection :as conn]
            [movie.server.handler :as server-handler]
            [movie.server.service :as service]
            [movie.storage :as storage]
            [movie.storage.atomic]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]))

(defn configure-logging!
  [{:keys [:movie/id :movie/log-path] :as config}]
  (let [log-file (str log-path "/" id "-" (util/uuid))]
    (log/merge-config!
     {:appenders {:spit (appenders/spit-appender
                         {:fname log-file})}})))

(s/def :movie/id string?)
(s/def :movie/port integer?)
(s/def :movie/log-path string?)
(s/def :movie/websocket-content-type string?)
(s/def :movie/user-manager-type #{:atomic})
(s/def :movie/movie-storage-type #{:atomic})
(s/def :movie/users (s/map-of :movie/username :movie/password))
(s/def :movie/moviedb-config map?)
(s/def :movie/config (s/keys :req [:movie/id
                                   :movie/port
                                   :movie/log-path
                                   :movie/user-manager-type
                                   :movie/movie-storage-type
                                   :movie/websocket-content-type]
                             :opt [:movie/users]))

(defn build
  [config]
  (log/info (str "Building " (:movie/id config) "."))
  (configure-logging! config)
  {:movie-storage (storage/movie-storage config)

   ;; User storage
   :user-manager (users/user-manager config)

   :event-bus (bus/event-bus)
   :moviedb-client (moviedb-client/client (:movie/moviedb-config config))
   :movie-searcher (search/searcher config)

   ;; HTTP
   :authenticator (auth/authenticator config)
   :conn-manager (conn/manager config)
   :handler-factory (server-handler/factory config)
   :app (service/aleph-service config)})

(defn system
  [config]
  (if-let [validation-failure (s/explain-data :movie/config config)]
    (do (log/error (str "Invalid configuration:\n"
                        (util/pretty config)
                        "Validation failure:\n"
                        (util/pretty validation-failure)))
        (throw (ex-info "Invalid configuration." {:config config
                                                  :validation-failure validation-failure})))
    (build config)))

(s/fdef system
  :args (s/cat :config :movie/config)
  :ret map?)
