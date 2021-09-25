(ns movie.backend.repo
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

(defn- dashed [x] (keyword (str/replace (name x) #"_" "-")))
(defn- underscored [x] (keyword (str/replace (name x) #"-" "_")))

(defn- get-id-column [table] (keyword (str (name (underscored table)) "_id")))

(defn- adjust-keys [table keys]
  (-> keys
      (set/rename-keys {:id (get-id-column table)})
      (update-keys underscored)))

(defn select-items
  ([db table]
   (select-items db table {}))
  ([db table {:keys [keys mappings relation]}]
   (let [id-column (get-id-column table)
         defaulted-mappings (or mappings {id-column :id})
         mapper #(-> %
                     (set/rename-keys defaulted-mappings)
                     (update-keys dashed))
         relation (underscored
                   (if relation
                     relation
                     (str (name table) "_view")))
         rows (if keys
                (sql/find-by-keys db relation (adjust-keys table keys))
                (let [sql (str "SELECT * FROM " (name relation))]
                  (sql/query db [sql])))]
     (map mapper rows))))

(defn insert-items! [db table cols items]
  (let [rows (map (fn [item] (map #(get item %) cols)) items)
        cols (map underscored cols)]
    (sql/insert-multi! db (underscored table) cols rows)))

(defn insert-item! [db table item]
  (sql/insert! db (underscored table) (update-keys item underscored)))

(defn update-items! [db table k cols items]
  (let [sets (->> cols
                  (map name)
                  (map #(str % " = ?"))
                  (str/join ", "))
        sql (str "UPDATE " (name (underscored table)) " SET " sets " WHERE " (name k) " = ?")
        bindings (mapv
                  (fn [item]
                    (conj
                     (mapv #(get item %) cols)
                     (get item k)))
                  items)]
    (jdbc/execute-batch! db sql bindings {})))

(defn deactivate-item! [db table keys]
  (sql/update! db (underscored table) {:active false} (adjust-keys table keys)))

(defn list-movies [db]
  (select-items db :movie))

(defn clear-movies! [db]
  (jdbc/execute! db ["DELETE FROM movie"]))

(def fields [:title :letter :path :release-date :overview :original-language :runtime :tmdb-id :imdb-id :tmdb-title :tmdb-popularity :tmdb-backdrop-path :tmdb-poster-path])

(defn insert-movies! [db movies]
  (insert-items! db :movie (conj fields :uuid) movies))

(defn update-movies! [db movies]
  (update-items! db :movie :uuid fields movies))

(defn get-movie [db keys]
  (first (select-items db :movie {:keys keys})))

(defn get-movie-id [db uuid]
  (:id (get-movie db {:uuid uuid})))

(defn rate-movie! [db uuid rating]
  (let [id (get-movie-id db uuid)]
    (deactivate-item! db :movie_rating {:id id})
    (sql/insert! db :movie-rating {:movie_id id :rating rating})))

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
