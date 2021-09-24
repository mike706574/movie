(ns movie.backend.info
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [movie.backend.date :as date]
            [movie.backend.tmdb :as tmdb]
            [taoensso.timbre :as log]))

(defn handle-date
  [date]
  (when-not (str/blank? date)
    (date/translate "yyyy-MM-dd" "MMMM dd, yyyy" date)))

(defn shorten
  [length string]
  (if (> (count string) length)
    (let [truncated (str/join (take length string))
          last-space-index (.lastIndexOf truncated " ")
          end-index (if (neg? last-space-index)
                      (count truncated)
                      last-space-index)
          spaced (subs truncated 0 end-index)
          end (if (str/ends-with? spaced ".")
                ""
                "...")]
      (str (str/trim spaced) end))))

(defn process-info
  [info]
  (-> info
      (set/rename-keys {:title :tmdb-title
                        :id :tmdb-id})
      (update :release-date handle-date)
      (update :overview (partial shorten 150))
      (select-keys [:title
                    :tmdb-title
                    :tmdb-id
                    :imdb-id
                    :release-date
                    :overview
                    :backdrop-path])))

(defn fetch-info
  [client title]
  (let [{:keys [status body] :as response} (tmdb/search-movies client title)]
    (if-not (= status :ok)
      {:status :error :body response}
      (let [results (:results body)]
        (case (count results)
          0 {:status :no-results}
          1 (let [info (first results)
                  {:keys [status body] :as response} (tmdb/get-movie client (:id info))]
              (if (= status :ok)
                {:status :ok :info (process-info (merge info body))}
                {:status :error :body response}))
          {:status :multiple-results :results results})))))
