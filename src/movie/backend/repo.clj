(ns movie.backend.repo
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [movie.common.util :as util]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [taoensso.timbre :as log]))

(defn- get-id-column [table] (keyword (str (name table) "_id")))

(defn- get-default-mappings [table] {(get-id-column table) :id})

(defn- adjust-keys [table keys]
  (set/rename-keys keys {:id (get-id-column table)}))

(defn select-items
  ([db table]
   (select-items db table {}))
  ([db table {:keys [keys mappings relation]}]
   (let [id-column (get-id-column table)
         defaulted-mappings (or mappings {id-column :id})
         mapper #(set/rename-keys % defaulted-mappings)
         relation (if relation
                    (name relation)
                    (str (name table) "_view"))
         rows (if keys
                (sql/find-by-keys db relation (adjust-keys table keys))
                (let [sql (str "SELECT * FROM " relation)]
                  (sql/query db [sql])))]
     (map mapper rows))))

(defn insert-items! [db table cols items]
  (let [rows (map (fn [item] (map #(get item %) cols)) items)]
    (sql/insert-multi! db table cols rows)))

(defn update-items! [db table k cols items]
  (let [sets (->> cols
                  (map name)
                  (map #(str % " = ?"))
                  (str/join ", "))
        sql (str "UPDATE " (name table) " SET " sets " WHERE " (name k) " = ?")
        bindings (mapv
                  (fn [item]
                    (conj
                     (mapv #(get item %) cols)
                     (get item k)))
                  items)]
    (jdbc/execute-batch! db sql bindings {})))

(defn deactivate-item! [db table keys]
  (sql/update! db table {:active false} (adjust-keys table keys)))

(defn list-movies [db]
  (select-items db :movie))

(defn clear-movies! [db]
  (jdbc/execute! db ["DELETE FROM movie"]))

(defn insert-movies! [db movies]
  (insert-items! db :movie [:uuid :title :letter :path] movies))

(defn update-movies! [db movies]
  (update-items! db :movie :uuid [:title :letter :path] movies))

(defn get-movie [db keys]
  (first (select-items db :movie {:keys keys})))

(defn get-movie-id [db uuid]
  (:id (get-movie db {:uuid uuid})))

(defn rate-movie! [db uuid rating]
  (let [id (get-movie-id db uuid)]
    (deactivate-item! db :movie_rating {:id id})
    (sql/insert! db :movie_rating {:movie_id id :rating rating})))

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
