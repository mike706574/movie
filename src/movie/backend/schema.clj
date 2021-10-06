(ns movie.backend.schema
  (:require [malli.util :as mu]))

(def audit-info
  [:map [:created inst?]])

(defn with-audit [schema]
  (mu/union schema audit-info))

(def account-model
  (with-audit [:map [:email string?]]))

(def movie-template
  [:map
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
     [:map
      [:uuid string?]
      [:rating [:maybe decimal?]]
      [:average-rating [:maybe decimal?]]]
     movie-template)))
