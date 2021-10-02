(ns movie.frontend.routing
  (:require [reitit.coercion.spec :as rcs]
            [reitit.frontend :as rfe]
            [reitit.frontend.easy :as rfez]
            [reitit.frontend.controllers :as rfc]
            [re-frame.core :as rf]))

(defn on-navigate [new-match]
  (when new-match
    (rf/dispatch [:navigated new-match])))

(defn router [routes]
  (rfe/router
    routes
    {:data {:coercion rcs/coercion}}))

(defn init-routes! [routes]
  (rfez/start!
    (router routes)
    on-navigate
    {:use-fragment true}))

(rf/reg-event-db
 :navigated
  (fn [db [_ new-match]]
    (let [old-match (:current-route db)
          controllers (rfc/apply-controllers (:controllers old-match) new-match)]
      (assoc db :current-route (assoc new-match :controllers controllers)))))

(rf/reg-fx :push-route
  (fn [route]
    (apply rfez/push-state route)))

(defn href
  ([k]
   (href k nil nil))
  ([k params]
   (href k params nil))
  ([k params query]
   (rfez/href k params query)))
