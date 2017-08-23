(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer [javadoc]]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [clojure.walk :as walk]
   [cognitect.transit :as transit]
   [com.stuartsierra.component :as component]
   [clojure.spec.alpha :as spec]
   [clojure.spec.test.alpha :as stest]

   [aleph.http :as http]
   [manifold.stream :as s]
   [manifold.deferred :as d]
   [manifold.bus :as bus]
   [taoensso.timbre :as log]

   [clojure.java.jdbc :as jdbc]

   [movie.client :as client]
   [movie.users :as users]
   [movie.server.system :as system]

   [movie.search :as search]
   [movie.storage :as storage]
   [movie.storage.postgres]
   [movie.file :as file]
   [boomerang.message :as message]))

(log/set-level! :debug)
(stest/instrument)

(def port 8001)
(def content-type "application/transit+json")

(def db
  {:dbtype "postgresql"
   :dbname "movie"
   :host "localhost"
   :user "postgres"
   :password "postgres"})

(def config {:movie/instance-id "movie-server"
             :movie/port port
             :movie/log-path "/tmp"
             :movie/secret-key "secret"
             :movie/websocket-content-type content-type
             :movie/movie-storage-type :postgres
             :movie/movie-storage-database db
             :movie/moviedb-config {:movie-api-url "https://api.themoviedb.org/3"
                                    :movie-api-key "7197608cef1572f5f9e1c5b184854484"
                                    :movie-api-retry-options {:initial-wait 1000
                                                              :max-attempts 20}}
             :movie/user-manager-type :atomic
             :movie/users {"mike" "rocket"}})

(defonce system nil)

(def url (str "http://localhost:" port))
(def ws-url (str "ws://localhost:" port "/api/websocket"))

(defn init
  "Creates and initializes the system under development in the Var
  #'system."
  []
  (alter-var-root #'system (constantly (system/system config)))
  :init)

(defn start
  "Starts the system running, updates the Var #'system."
  []
  (try
    (alter-var-root #'system component/start-system)
    :started
    (catch Exception ex
      (log/error (or (.getCause ex) ex) "Failed to start system.")
      :failed)))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop-system s))))
  :stopped)

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start)
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after `go))

(defn restart
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (go))

(def searcher (:movie-searcher system))
(def storage (:movie-storage system))

(defn load-all
  [searcher path]
  (map
   (fn [movie]
     (-> movie
         (merge (search/feeling-lucky searcher (:title movie)))
         (assoc :watched [])))
   (file/load path)))

(def client #(-> {:host (str "localhost:" port)
                  :content-type content-type}
                 (client/client)
                 (client/authenticate {:movie/username "mike"
                                       :movie/password "rocket"})))


(def storage #(:movie-storage system))

(comment
  (client/get-movies (client) {:page 1})

  (storage/unwatched! (storage) "1" "mike")

  (storage/clear! (storage))

  (storage/get-page (storage) 0)

  (doseq [movie (load-all searcher "/media/mike/Clone/Video")]
    (storage/add-movie! (storage) movie)
    (println (:title movie)))

  (def akira (first (load-all searcher "dev-resources/movies")))

  akira

  (storage/add-movie! storage akira)

  (storage/get-movies storage)

  (jdbc/query db ["select count(*) from movie"])
  system
  )
