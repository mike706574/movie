(ns movie.backend.schema
  (:require [malli.core :as m]
            [malli.util :as mu]))

(def audit-info
  [:map {:closed true}
   [:created inst?]])

(defn with-audit [schema]
  (mu/union schema audit-info))

(def email-and-password-params
  [:map
   [:email string?]
   [:password string?]])

(def uuid-params
  [:map
   [:uuid string?]])

(def account-model
  (with-audit [:map {:closed true}
               [:email string?]]))

(def movie-template
  [:map {:closed true}
   [:title string?]
   [:letter string?]
   [:path string?]
   [:release-date [:maybe string?]]
   [:overview [:maybe string?]]
   [:original-language [:maybe string?]]
   [:runtime [:maybe int?]]
   [:tmdb-id [:maybe int?]]
   [:imdb-id [:maybe string?]]
   [:tmdb-title [:maybe string?]]
   [:tmdb-popularity [:maybe decimal?]]
   [:tmdb-backdrop-path [:maybe string?]]
   [:tmdb-poster-path [:maybe string?]]])

(def movie-model
  (with-audit
    (mu/union
     [:map {:closed true}
      [:uuid string?]]
     movie-template)))
