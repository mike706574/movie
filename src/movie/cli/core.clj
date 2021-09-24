(ns movie.cli.core
  (:require [clojure.set :as set]
            [movie.common.storage :as storage]
            [movie.common.client :as client]
            [movie.common.tmdb :as tmdb]
            [taoensso.timbre :as log]))

(defn list-movies
  [{:keys [client]}]
  (client/list-movies client))

(defn rand-uuid []
  (.toString (java.util.UUID/randomUUID)))

(defn process-info
  [info]
  (-> info
      (set/rename-keys {:title :tmdb-title
                        :id :tmdb-id})
      (select-keys [:tmdb-title
                    :tmdb-id
                    :imdb-id
                    :release-date
                    :overview
                    :backdrop-path])))

(defn fetch-info
  [tmdb title]
  (let [{:keys [status body] :as response} (tmdb/search-movies tmdb title)]
    (if-not (= status :ok)
      {:status :error :body response}
      (let [results (:results body)]
        (case (count results)
          0 (do (log/info "No results found for title." {:title title})
                nil)
          1 (let [info (first results)
                  id (:id info)
                  {:keys [status body] :as response} (tmdb/get-movie tmdb id)]
              (if (= status :ok)
                (process-info (merge info body))
                (log/info "Failed to pull info." {:id id
                                                  :body body})))
          (do (log/info "Multiple results found for title." {:title title
                                                             :results results})
              nil))))))

;; (log/info "one")

(defn sync-movies!
  [{:keys [path client tmdb]}]
  (let [raw-movies (storage/read-movies-dir path)
        movies (map
                (fn [movie]
                  (let [{:keys [title uuid path]} movie]
                    (if uuid
                      movie
                      (let [new-uuid (rand-uuid)
                            info (fetch-info tmdb title)
                            metadata (assoc info :uuid uuid)]
                        (log/info "Populating metadata" {:path path :uuid new-uuid})
                        (storage/write-metadata! path metadata)
                        (assoc movie :uuid new-uuid)))))
                raw-movies)]
    (println "MOVIES" movies)
    (client/sync-movies! client movies)))
