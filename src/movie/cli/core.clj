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
  (let [{:keys [status body] :as response} (tmdb/search-movies tmdb title {:limit 15})]
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

(defn resolve-movie-info [tmdb title]
  (let [all (search-tmdb-movies tmdb title)
        {matches true others false} (group-by #(= (:tmdb-title %) title) all)
        sorted-matches (sort-by :tmdb-popularity #(compare %2 %1) matches)
        movies (take 10 (concat sorted-matches others))]
    (if (empty? movies)
      (do
        (println "No movies found.")
        (print "Continue: ")
        (flush)
        (read-line)
        {})
      (let [title-width (apply max (map #(count (:tmdb-title %)) movies))]
        (doseq [[idx movie] (map-indexed vector movies)]
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
                          (if (< 0 num (inc (count movies)))
                            num
                            (recur))))))]
          (when num
            (get (vec movies) (dec num))))))))

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
                      (do (println title "-" path)
                          (if-let [info (resolve-movie-info tmdb title)]
                            (let [uuid (if uuid uuid (rand-uuid))
                                  metadata (assoc info :uuid uuid)]
                              (println "Writing metadata.")
                              (storage/write-metadata! path metadata)
                              (merge movie metadata))
                            (println "Not writing metadata."))
                        ))))
                raw-movies)]
    (println "Syncing" (count movies) "movies.")
    (client/sync-movies! client movies)))
