(ns movie.search
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [movie.moviedb-client :as moviedb]))

(defn parse-date
  [date]
  (when-not (str/blank? date)
    (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd") date)))

(defn error [response]
  (when-not (= :ok (:status response))
    response))

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
      (update :release-date parse-date)))

(defn search
  [client title]
  (let [response (moviedb/search-movies client title)]
    (or (error response)
        {:status :ok :body (map movie (-> response :body :results))})))

(defn nil-search
  [client start title]
  (let [{:keys [status body]} (search client title)]
    (if (= status :ok)
      (when-not (empty? body)
        body)
      (throw (ex-info status body)))))

(defn lazy-search
  ([client title]
   (lazy-search client title 1 []))

  ([client title start page]
   (lazy-seq
    (when-let [page (if (empty? page)
                      (nil-search client start title)
                      page)]
      (cons (first page) (lazy-search client title (inc start) (rest page)))))))


(comment
  (def config {:movie-api-url "https://api.themoviedb.org/3"
               :movie-api-key "7197608cef1572f5f9e1c5b184854484"
               :movie-api-retry-options {:initial-wait 0
                                         :max-attempts 3}})

  (def client (moviedb/client config))

  (count (lazy-search client "blue"))
  )
