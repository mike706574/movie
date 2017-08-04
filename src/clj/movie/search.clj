(ns movie.search
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [movie.moviedb-client :as moviedb]))

(defprotocol MovieSearcher
  (search [this query] [this query page]))

(defn parse-date
  [date]
  (when-not (str/blank? date)
    (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd") date)))

(def key-mapping {:title :tmdb-title
                  :id :tmdb-id
                  :imdb_id :imdb-id
                  :release_date :release-date
                  :overview :overview
                  :backdrop_path :backdrop-path})

(defn movie [movie]
  (-> movie
      (select-keys (keys key-mapping))
      (set/rename-keys key-mapping)
;;      (update :release-date parse-date)
      ))

(defn ^:private search*
  ([client title]
   (search* client title 1))
  ([client title page-number]
   (let [response (moviedb/search-movies client title page-number)]
     (when (= :ok (:status response))
         (-> response
             (update :body #(set/rename-keys % {:total_results :total-results
                                                :total_pages :total-pages}))
             (update-in [:body :results] (partial map movie)))))))

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
      (throw (Exception. (str "Search request returned status " status "."))))))

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

(defn from-client
  [client]
  (map->TMDbMovieSearcher {:client client}))

(defn searcher
  [config]
  (component/using (map->TMDbMovieSearcher {}) {:client :moviedb-client}))

(comment
  (def config {:movie-api-url "https://api.themoviedb.org/3"
               :movie-api-key "7197608cef1572f5f9e1c5b184854484"
               :movie-api-retry-options {:initial-wait 0
                                         :max-attempts 3}})

  (def client (moviedb/client config))
  (def searcher (from-client client))

  (count (search searcher "blue"))
  )
