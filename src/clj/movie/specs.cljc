(ns movie.specs
  (:require [clojure.spec.alpha :as s]))

(s/def :movie/tmdb-title string?)
(s/def :movie/tmdb-id integer?)
(s/def :movie/release-date string?)
(s/def :movie/overview string?)
(s/def :movie/backdrop-path string?)

(s/def :movie/movie (s/keys :req-un [:movie/tmdb-title
                                     :movie/tmdb-id
                                     :movie/release-date
                                     :movie/overview
                                     :movie/backdrop-path]))
