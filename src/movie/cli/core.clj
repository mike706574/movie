(ns movie.cli.core
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [movie.common.storage :as storage]
            [movie.common.client :as client]
            [movie.common.tmdb :as tmdb]
            [taoensso.timbre :as log]))

(defn ellipsis [length string]
  (if (> (count string) length)
    (str (str/trim (str/join (take length string))) "...")
    string))

(defn rand-uuid []
  (.toString (java.util.UUID/randomUUID)))

(defn list-movies [{:keys [client]}]
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
  (let [{:keys [status body] :as response} (tmdb/search-movies tmdb title {:limit 5})]
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
  (println title)
  (let [movies (search-tmdb-movies tmdb title)]
    (if (empty? movies)
      {}
      (let [selected-movies (vec (take 5 movies))
            title-width (apply max (map #(count (:tmdb-title %)) selected-movies))]
        (if (empty? movies)
          (do
            (println "No movies found.")
            (println "Continue: ")
            (flush)
            (read-line)
            nil)
          (do
            (doseq [[idx movie] (map-indexed vector selected-movies)]
              (let [{:keys [release-date tmdb-title overview tmdb-popularity]} movie]
                (println (inc idx) "|" (right-pad title-width tmdb-title) "|" release-date "|" tmdb-popularity "|" (ellipsis 50 overview))))
            (let [num (loop []
                        (print "Choose: ")
                        (flush)
                        (let [input (read-line)]
                          (case input
                            "q" (throw (ex-info "Quit" {}))
                            "s" nil
                            (let [num (parse-num input)]
                              (if (< 0 num (inc (count selected-movies)))
                                num
                                (recur))))))]
              (when num
                (get selected-movies (dec num))))))))))

(defn sync-movies! [{:keys [sources client tmdb]}]
  (let [raw-movies (mapcat
                    (fn [{:keys [kind path category] :as source}]
                      (log/info "Reading movies from source" source)
                      (let [movies (case kind
                                     "root-dir" (storage/read-root-dir path)
                                     "category-dir" (storage/read-category-dir path category)
                                     (throw (ex-info "Invalid source kind" {:kind kind})))]
                        (log/info "Read movies from source" (assoc source :count (count movies)))
                        movies))
                    sources)
        movies (map
                (fn [movie]
                  (let [{:keys [path title tmdb-id uuid]} movie]
                    (if tmdb-id
                      movie
                      (let [uuid (if uuid uuid (rand-uuid))
                            info (resolve-movie-info tmdb title)
                            metadata (assoc info :uuid uuid)]
                        (log/info "Populating metadata" metadata)
                        (storage/write-metadata! path metadata)
                        (merge movie metadata)))))
                raw-movies)]
    (println "Syncing" (count movies) "movies.")
    (client/sync-movies! client movies)))
