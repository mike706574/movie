(ns movie.backend.repo
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

(defn- dashed [x] (keyword (str/replace (name x) #"_" "-")))
(defn- underscored [x] (keyword (str/replace (name x) #"-" "_")))

(defn- adjust-keys [keys]
  (update-keys keys underscored))

(defn select-items
  ([db table]
   (select-items db table {}))
  ([db table {:keys [keys mappings]}]
   (let [mapper #(update-keys % dashed)
         rows (if keys
                (sql/find-by-keys db (underscored table) (adjust-keys keys))
                (let [sql (str "SELECT * FROM " (name (underscored table)))]
                  (sql/query db [sql])))]
     (map mapper rows))))

(defn insert-items! [db table cols items]
  (let [rows (map (fn [item] (map #(get item %) cols)) items)
        cols (map underscored cols)]
    (sql/insert-multi! db (underscored table) cols rows)))

(defn insert-item! [db table item]
  (sql/insert! db (underscored table) (update-keys item underscored)))

(defn update-items! [db table primary-key keys items]
  (let [sets (->> keys
                  (map underscored)
                  (map name)
                  (map #(str % " = ?"))
                  (str/join ", "))
        primary-key-col (name (underscored primary-key))
        sql (str "UPDATE " (name (underscored table)) " SET " sets " WHERE " primary-key-col " = ?")
        bindings (mapv
                  (fn [item]
                    (conj
                     (mapv #(get item %) keys)
                     (get item primary-key)))
                  items)]
    (jdbc/execute-batch! db sql bindings {})))

(defn get-movie [db keys]
  (first (select-items db :movie-view {:keys keys})))

(defn get-account [db keys]
  (first (select-items db :account {:keys keys})))

(defn get-movie-id [db uuid]
  (:movie-id (get-movie db {:uuid uuid})))

(defn get-account-id [db email]
  (:account-id (get-account db {:email email})))

(defn list-accounts [db]
  (select-items db :account-view))

(defn insert-account! [db account]
  (insert-item! db :account account))

(defn deactivate-item! [db table keys]
  (sql/update! db (underscored table) {:active false} (adjust-keys keys)))

(defn list-movies [db]
  (select-items db :movie-view))

(defn list-account-movies [db email]
  (select-items db :account-movie-view {:keys {:account-id (get-account-id db email)}}))

(defn get-account-movie [db email keys]
  (first (select-items db :account-movie-view {:keys (assoc keys :account-id (get-account-id db email))})))

(defn clear-movies! [db]
  (jdbc/execute! db ["DELETE FROM movie"]))

(def movie-fields [:title :letter :path :release-date :overview :original-language :runtime :tmdb-id :imdb-id :tmdb-title :tmdb-popularity :tmdb-backdrop-path :tmdb-poster-path])

(defn insert-movies! [db movies]
  (insert-items! db :movie (conj movie-fields :uuid) movies))

(defn update-movies! [db movies]
  (update-items! db :movie :uuid movie-fields movies))

(defn rate-movie! [db uuid email rating]
  (let [movie-id (get-movie-id db uuid)
        account-id (get-account-id db email)]
    (deactivate-item! db :movie-rating {:movie-id movie-id :account-id account-id})
    (insert-item! db :movie-rating {:movie-id movie-id :account-id account-id :rating rating})))

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
