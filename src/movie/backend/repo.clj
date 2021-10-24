(ns movie.backend.repo
  (:require [movie.backend.db :as db]))

(defn get-movie [db keys]
  (first (db/select-items db :movie-view {:keys keys})))

(def account-fields [:email :created])

(defn get-account-with-password [db keys]
  (first (db/select-items db :account {:keys keys
                                       :cols (conj account-fields :password)})))

(defn get-account [db keys]
  (first (db/select-items db :account {:keys keys
                                       :cols account-fields})))

(defn list-accounts [db]
  (db/select-items db :account {:cols account-fields
                                :order-by [:email]}))

(defn get-movie-id [db uuid]
  (:movie-id (get-movie db {:uuid uuid})))

(defn get-account-id [db email]
  (-> (db/select-items db :account {:keys {:email email}
                                    :cols [:account-id]})
      first
      :account-id))

(defn insert-account! [db account]
  (db/insert-item! db :account account))

(defn list-movies [db]
  (db/select-items db :movie-view {:order-by [:title]}))

(defn list-account-movies [db email]
  (let [account-id (get-account-id db email)]
    (db/select-items db :account-movie-view {:keys {:account-id account-id}
                                             :order-by [:title]})))

(defn get-account-movie [db email keys]
  (let [account-id (get-account-id db email)
        keys (assoc keys :account-id account-id)]
    (db/select-first-item db :account-movie-view {:keys keys})))

(defn clear-movies! [db]
  (db/clear-items! db :movie))

(def movie-fields
  [:title
   :category
   :path
   :release-date
   :overview
   :original-language
   :runtime
   :tmdb-id
   :imdb-id
   :tmdb-title
   :tmdb-popularity
   :tmdb-backdrop-path
   :tmdb-poster-path])

(defn insert-movies! [db movies]
  (db/insert-items! db :movie (conj movie-fields :uuid) movies))

(defn update-movies! [db movies]
  (db/update-items! db :movie :uuid movie-fields movies))

(defn rate-movie! [db uuid email rating]
  (let [movie-id (get-movie-id db uuid)
        account-id (get-account-id db email)]
    (db/deactivate-item! db :movie-rating {:movie-id movie-id :account-id account-id})
    (db/insert-item! db :movie-rating {:movie-id movie-id :account-id account-id :rating rating})))

(defn sync-movies! [db movies]
  (let [stored-movies (list-movies db)
        stored-uuids (->> stored-movies
                          (map :uuid)
                          (into #{}))
        classify-movie (fn [{uuid :uuid}]
                         (cond
                           (nil? uuid) :invalid-movies
                           (stored-uuids uuid) :existing-movies
                           :else :new-movies))
        {:keys [invalid-movies existing-movies new-movies]} (group-by classify-movie movies)]
    (insert-movies! db new-movies)
    (update-movies! db existing-movies)
    {:invalid-movie-count (count invalid-movies)
     :invalid-movie-titles (mapv :title invalid-movies)
     :new-movie-count (count new-movies)
     :new-movie-titles (mapv :title new-movies)
     :existing-movie-count (count existing-movies)}))
