(ns movie.backend.service
  (:require [aleph.http :as aleph]
            [com.stuartsierra.component :as component]
            [movie.backend.handler :as handler]
            [taoensso.timbre :as log]))

(defn info [service]
  (select-keys service [:port]))

(defn- already-started
  [service]
  (log/info "Service already started.")
  service)

(defn- start-service
  [{:keys [port handler-factory] :as service}]
  (let [info (info service)]
    (try
      (log/info "Starting service." info)
      (let [handler (handler/build handler-factory)
            server (aleph/start-server handler {:port port})]
        (log/info "Finished starting service." info)
        (assoc service :server server))
      (catch java.net.BindException _
        (throw (ex-info "Service port already in use." info))))))

(defn- stop-service
  [{:keys [port server] :as service}]
  (log/info "Stopping service." {:port port})
  (.close server)
  (dissoc service :server))

(defn- already-stopped
  [service]
  (log/info "Service already stopped.")
  service)

(defrecord Service [port server handler-factory]
  component/Lifecycle
  (start [this]
    (if server
      (already-started this)
      (start-service this)))
  (stop [this]
    (if server
      (stop-service this)
      (already-stopped this))))

(defn service
  [{:keys [port] :as config}]
  {:pre [(integer? port)
         (> port 0)]}
  (component/using (map->Service config) [:handler-factory]))
