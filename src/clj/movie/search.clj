(ns movie.search
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [movie.moviedb-client :as moviedb]
            [taoensso.timbre :as log]))

(defprotocol MovieSearcher
  (search [this query] [this query page]))

(def ^:private key-mapping {:title :tmdb-title
                            :id :tmdb-id
                            :imdb_id :imdb-id
                            :release_date :release-date
                            :overview :overview
                            :backdrop_path :backdrop-path})

(defn ^:private movie [movie]
  (-> movie
      (select-keys (keys key-mapping))
      (set/rename-keys key-mapping)))

(defn ^:private search*
  ([client title]
   (search* client title 1))
  ([client title page-number]
   (let [response (moviedb/search-movies client title page-number)]
     (if (= :ok (:status response))
       (-> response
           (update :body #(set/rename-keys % {:total_results :total-results
                                              :total_pages :total-pages}))
           (update-in [:body :results] (partial map movie)))
       response))))

(defrecord TMDbMovieSearcher [client]
  MovieSearcher
  (search [this query]
    (search* client query))

  (search [this query page]
    (search* client query page)))

(defn ^:private search-and-throw!
  [searcher title page-number]
  (let [{:keys [status body] :as response} (search searcher title page-number)]
    (if (= status :ok)
      body
      (do (log/error response)
        (throw (ex-info (str "Search for \"" title "\", page " page-number " failed.") response))))))

(defn ^:private lazy-search*
  [searcher title data]
  (when-not (and (= (:page data) (:total-pages data))
                 (empty? (:results data)))
    (let [data (if (empty? (:results data))
                 (search-and-throw! searcher title (inc (:page data)))
                 data)]
      (cons (:tmdb-title (first (:results data)))
            (lazy-seq (lazy-search* searcher title (update data :results rest)))))))

(defn lazy-search
  [searcher title]
  (lazy-search* searcher title (search-and-throw! searcher title 1)))

(defn feeling-lucky
  [searcher title]
  (first (:results (search-and-throw! searcher title 1))))

(defn from-client
  [client]
  (map->TMDbMovieSearcher {:client client}))

(defn searcher
  [config]
  (component/using (map->TMDbMovieSearcher {}) {:client :moviedb-client}))
