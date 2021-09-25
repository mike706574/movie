(ns movie.cli.core
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [movie.common.storage :as storage]
            [movie.common.client :as client]
            [movie.common.tmdb :as tmdb]
            [taoensso.timbre :as log]))

(defn ellipsis
  [length string]
  (if (> (count string) length)
    (str (str/trim (str/join (take length string))) "...")
    string))

(defn rand-uuid []
  (.toString (java.util.UUID/randomUUID)))

(defn list-movies
  [{:keys [client]}]
  (client/list-movies client))

(defn process-info
  [info]
  (-> info
      (select-keys [:id
                    :title
                    :imdb-id
                    :release-date
                    :overview
                    :runtime
                    :popularity
                    :original-language
                    :backdrop-path
                    :poster-path])
      (update :release-date #(if (str/blank? %) nil %))
      (set/rename-keys {:title :tmdb-title
                        :id :tmdb-id
                        :popularity :tmdb-popularity
                        :backdrop-path :tmdb-backdrop-path
                        :poster-path :tmdb-poster-path})))

(defn fetch-tmdb-movie
  [tmdb id]
  (let [{:keys [status body]} (tmdb/get-movie tmdb id)]
    (if (= status :ok)
      (process-info body)
      (do (log/info "Failed to pull info." {:id id :body body})
          nil))))

(defn search-tmdb-movies
  [tmdb title]
  (let [{:keys [status body] :as response} (tmdb/search-movies tmdb title)]
    (if-not (= status :ok)
      {:status :error :body response}
      (->> body
           (map #(fetch-tmdb-movie tmdb (:id %)))
           (filter identity)))))

(defn parse-num [input]
  (try
    (Integer/parseInt input)
    (catch NumberFormatException _
      -1)))

(defn right-pad [n s] (format (str "%-" n "s") s))

(defn resolve-movie-info
  [tmdb title]
  (let [movies (search-tmdb-movies tmdb title)
        selected-movies (vec (take 5 movies))
        title-width (apply max (map #(count (:tmdb-title %)) selected-movies))]
    (when (seq movies)
      (println title "-" (count movies) "results")
      (doseq [[idx movie] (map-indexed vector selected-movies)]
        (let [{:keys [release-date tmdb-title overview tmdb-popularity]} movie]
          (println (inc idx) "|" (right-pad title-width tmdb-title) "|" release-date "|" tmdb-popularity "|" (ellipsis 50 overview))))
      (let [num (loop []
                  (print "Choose: ")
                  (let [input (read-line)]
                    (if (= input "q")
                      (throw (ex-info "Quit" {}))
                      (let [num (parse-num input)]
                        (print \newline)
                        (if (< 0 num (inc (count selected-movies)))
                          num
                          (recur))))))]
        (get selected-movies (dec num))))))

(defn sync-movies!
  [{:keys [path client tmdb]}]
  (let [raw-movies (storage/read-movies-dir path)
        movies (map
                (fn [movie]
                  (let [{:keys [title uuid path]} movie]
                    (if uuid
                      movie
                      (let [new-uuid (rand-uuid)
                            info (resolve-movie-info tmdb title)
                            metadata (assoc info :uuid new-uuid)]
                        (log/info "Populating metadata" metadata)
                        (storage/write-metadata! path metadata)
                        (merge movie metadata)))))
                raw-movies)]
    (client/sync-movies! client movies)))
