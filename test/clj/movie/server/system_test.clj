(ns movie.server.system-test
  (:require [aleph.http :as http]
            [movie.client :as client]
            [movie.macros :refer [with-system]]
            [boomerang.message :as message]
            [movie.server.system :as system]
            [movie.util :as util :refer [map-vals]]
            [com.stuartsierra.component :as component]
            [clojure.test :refer [deftest testing is]]
            [manifold.bus :as bus]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [taoensso.timbre :as log]))

(def port 9001)
(def content-type "application/transit+json")
(def config {:movie/id "movie-server"
             :movie/port port
             :movie/log-path "/tmp"
             :movie/user-manager-type :atomic
             :movie/movie-storage-type :atomic
             :movie/moviedb-config {:movie-api-url "https://api.themoviedb.org/3"
                                    :movie-api-key "7197608cef1572f5f9e1c5b184854484"
                                    :movie-api-retry-options {:initial-wait 0
                                                              :max-attempts 3}}
             :movie/websocket-content-type content-type
             :movie/users {"mike" "rocket"}})

(defmacro unpack-response
  [call & body]
  `(let [~'response ~call
         ~'status (:status ~'response)
         ~'body (:body ~'response)
         ~'text (util/pretty ~'response)]
     ~@body))

(log/set-level! :debug)
(deftest simple-test
  (with-system (system/system config)
    (let [client (-> {:host (str "localhost:" port)
                      :content-type content-type}
                     (client/client)
                     (client/authenticate {:movie/username "mike"
                                           :movie/password "rocket"}))]
      (unpack-response (client/moviedb-search client "fantasia" 1)
        (is (= status 200))

        )))
  )
