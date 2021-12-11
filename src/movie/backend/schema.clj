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
   [:category string?]
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
      [:owned {:optional true} [:maybe boolean?]]
      [:watched {:optional true} [:maybe boolean?]]
      [:rating {:optional true} [:maybe decimal?]]
      [:average-rating {:optional true} [:maybe decimal?]]]
     movie-template)))
