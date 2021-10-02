(ns movie.frontend.storage
  (:require [clojure.edn :as edn]
            [re-frame.core :as rf]))

(defn set-item [key val]
  (.setItem (.-localStorage js/window) (name key) (pr-str val)))

(defn remove-item [key]
  (.removeItem (.-localStorage js/window) (name key)))

(defn get-item [key]
  (edn/read-string (.getItem (.-localStorage js/window) (name key))))

(rf/reg-fx
 :storage
 (fn [changes]
   (doseq [[type key val] changes]
     (case type
       :set (set-item key val)
       :remove (remove-item key)
       (throw (ex-info "Invalid local storage change." {:type type :key key :val val}))))))

(defn get-account []
  (get-item :account))

(defn get-token []
  (:token (get-account)))
