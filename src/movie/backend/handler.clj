(ns movie.backend.handler
  (:require [com.stuartsierra.component :as component]
            [movie.backend.repo :as repo]
            [movie.common.tmdb :as tmdb]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.util.response :as resp]
            [selmer.parser :as selmer]
            [taoensso.timbre :as log]))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri method] :as request}]
    (let [label (str method " \"" uri "\"")]
      (try
        (log/info label)
        (let [{:keys [status] :as response} (handler request)]
          (log/info (str label " -> " status))
          response)
        (catch Exception e
          (log/error e label)
          {:status 500})))))

(defn routes
  [{:keys [db tmdb]}]
  [["/" {:get {:parameters {}
               :responses  {200 {:body any?}}
               :handler (fn [_]
                          (resp/resource-response "public/index.html"))}}]

   ["/movies/:uuid" {:get {:parameters {}
                           :responses {200 {:body any?}}
                           :handler (fn [{{{uuid :uuid} :path} :parameters}]
                                      (let [movie (repo/get-movie db uuid)]
                                        {:status 200
                                         :headers {"Content-Type" "text/html"}
                                         :body (selmer/render-file "templates/movie.html" movie)}))}}]

   ["/api/movies" {:get {:parameters {}
                         :responses {200 {:body any?}}
                         :handler (fn [_]
                                    {:status 200 :body (repo/list-movies db)})}
                   :post {:parameters {:body any?}
                          :responses {200 {:body any?}}
                          :handler (fn [{{movies :body} :parameters}]
                                     (let [result (repo/sync-movies! db movies)]
                                       {:status 200 :body result}))}}]

   ["/tmdb/search" {:get {:parameters {:query {:title string?}}}
                    :responses {200 {:body any?}}
                    :handler (fn [{{{:keys [title]} :query} :parameters}]
                               (log/info "TMDB search" {:title title})
                               (let [results (tmdb/search-movies tmdb title)]
                                 {:status 200 :body results}))}]

   ["/*" (ring/create-resource-handler)]])

(defn router [deps]
  (ring/router
   (routes deps)
   {:data {:coercion reitit.coercion.spec/coercion
           :muuntaja m/instance
           :middleware [muuntaja/format-middleware
                        parameters/parameters-middleware
                        rrc/coerce-request-middleware
                        rrc/coerce-response-middleware]}
    :conflicts (constantly nil)}))

(defn handler [deps]
  (->> deps
       router
       ring/ring-handler
       wrap-logging))

(defprotocol IHandlerFactory
  (build [hf]))

(defrecord HandlerFactory [db movies tmdb]
  IHandlerFactory
  (build [this]
    (handler this))
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn factory []
  (component/using
   (map->HandlerFactory {})
   [:db :tmdb]))
