(ns movie.fun
  (:require [movie.search :as search]
            [movie.file :as file]))

(def config
  {:movie-api-url "https://api.themoviedb.org/3"
   :movie-api-key "7197608cef1572f5f9e1c5b184854484"
   :movie-api-retry-options {:initial-wait 0
                             :max-attempts 3}})


(search/searcher config)
