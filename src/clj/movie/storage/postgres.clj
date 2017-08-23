(ns movie.storage.postgres
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            [movie.storage :refer [movie-storage]]
            [taoensso.timbre :as log])
  (:import [movie.storage MovieStorage]))

(defmacro wrap-sql-errors
  [& body]
  `(try
     ~@body
     (catch java.sql.SQLException e#
       (log/error e#)
       {:status :sql-error
        :message (.getMessage e#)
        :exception-type (.getName (.getClass e#))})))

(def parse-int #(if (string? %) (Integer/parseInt %) %))


(def movie-keys [:id :title :letter :directory :overview :tmdb-id :imdb-id :tmdb-title :backdrop-path])
(def row-keys [:id :title :letter :directory :overview :tmdb_id :imdb_id :tmdb_title :backdrop_path])

(def movie-columns (str/join ", " (map name row-keys)))

(def select-movies (str "select " movie-columns  ", username from movie left outer join watched on movie.id = watched.movie_id"))
(def order-by-title "order by title")
(def where-movie-is "where movie.id = ?")
(def where-movie-letter-is "where movie.letter = ?")

(def movie->row #(-> %
                     (select-keys movie-keys)
                     (set/rename-keys (zipmap movie-keys row-keys))
                     (dissoc :id)))

(defn row->movie
  [{username :username :as row}]
  (-> row
      (select-keys row-keys)
      (set/rename-keys (zipmap row-keys movie-keys))
      (assoc :watched (if username #{username} #{}))
      (update :id str)))

(defn merge-movie
  [movies {id :id username :username :as row}]
  (update movies id #(if %
                       (update % :watched conj username)
                       (row->movie row))))

(defn insert-watched!
  [db id username]
  (let [row {:movie_id (parse-int id) :username username}
        rows (jdbc/insert! db :watched row)]
    (when (seq rows)
      {:movie-id id :username username})))

(defn insert-file!
  [db id file-name file-type]
  (let [db-id (parse-int id)]
    (jdbc/insert! db :file {:movie_id db-id
                            :file_name file-name
                            :file_type file-type})))

(defn movies-response
  [count rows]
  {:status :ok
   :movie-count count
   :movies (vals (reduce merge-movie {} rows))})

(defn page-clause
  [page-size page-number]
  (str "offset " (* page-size (dec page-number)) " limit " page-size))

(defn total-count
  [db]
  (:count (first (jdbc/query db [(str "select count(*) from movie")]))))

(def page-size 12)

(defn letter-count
  [db letter]
  (let [query (str "select count(*) from movie " where-movie-letter-is)]
    (:count (first (jdbc/query db [query letter])))))

(defn page-count
  [movie-count]
  (+ (quot movie-count page-size)
     (if (zero? (rem movie-count page-size)) 0 1)))

(defrecord PostgresMovieStorage [db]
  MovieStorage
  (get-movie [this id]
    (wrap-sql-errors
     (let [db-id (parse-int id)
           rows (jdbc/query db [(str select-movies " " where-movie-is) id])
           movie (get (reduce merge-movie {} rows) id)]
       {:status :ok :movie movie})))

  (get-movies [this]
    (wrap-sql-errors
     (let [movie-count (total-count db)
           rows (jdbc/query db [(str select-movies " " order-by-title)])]
       (log/spy (movies-response movie-count rows)))))

  (get-page [this page-number]
    (if (< page-number 1)
      {:status :invalid-args :message (str "Page number must be 1 or greater.")}
      (wrap-sql-errors
       (let [movie-count (total-count db)
             page-count (page-count movie-count)
             query (str select-movies " " order-by-title " " (page-clause page-size page-number))
             rows (jdbc/query db [query])]
         (assoc (movies-response movie-count rows) :page-count page-count)))))

  (get-movies-by-letter [this letter]
    (wrap-sql-errors
     (let [movie-count (letter-count db letter)
           query (str select-movies " " where-movie-letter-is " " order-by-title)
           rows (jdbc/query db [query letter])]
       (movies-response movie-count rows))))

  (get-page-by-letter [this letter page-number]
    (if (< page-number 1)
      {:status :invalid-args :message (str "Page number must be 1 or greater.")}
      (wrap-sql-errors

       (let [movie-count (letter-count db letter)
             page-count (page-count movie-count)
             query (str select-movies " " where-movie-letter-is " " order-by-title " " (page-clause page-size page-number))
             rows (jdbc/query db [query letter])]
         (assoc (movies-response movie-count rows) :page-count page-count)))))

  (watched!
    [this id username]
    (wrap-sql-errors
     (if-let [entry (insert-watched! db id username)]
       {:status :ok :entry entry}
       {:status :error :message (str "No entry was inserted for movie " id " and user " username ".")})))

  (unwatched!
    [this id username]
    (wrap-sql-errors
     (let [db-id (parse-int id)
           where ["movie_id = ? and username = ?" db-id]
           row-count (jdbc/delete! db :watched where)]
       (if (= row-count 1)
         {:status :ok :entry {:movie-id id :user username}}
         {:status :missing :message (str "No entry found for movie " id " and user " username ".")}))))

  (add-movie! [this movie]
    (wrap-sql-errors
      (let [row (-> movie
                    (dissoc :id)
                    (movie->row))
            id (:id (first (jdbc/insert! db :movie row)))]
        (doseq [video (:videos movie)]
          (insert-file! db id video "video"))
        (doseq [subtitle (:subtitles movie)]
          (insert-file! db id subtitle "subtitle"))
        (doseq [username (:watched movie)]
          (insert-watched! this id username))
        {:status :ok :movie (assoc movie :id id)})))

  (clear! [this]
    (wrap-sql-errors
     (jdbc/execute! db ["delete from file"])
     (jdbc/execute! db ["delete from watched"])
     (jdbc/execute! db ["delete from movie"])
     {:status :ok})))

(defmethod movie-storage :postgres
  [config]
  (PostgresMovieStorage. (:movie/movie-storage-database config)))
