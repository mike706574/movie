(ns movie.users.jdbc
  (:require [movie.database.misc :as misc]
            [movie.users :refer [user-manager]]
            [buddy.hashers :as hashers]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [taoensso.timbre :as log])
  (:import [movie.users UserManager]))

(def user-table-ddl
  (jdbc/create-table-ddl :user
                         [[:id :serial "PRIMARY KEY"]
                          [:username "varchar(32)" "NOT NULL"]
                          [:password "char(128)" "NOT NULL"]]))

(defn create-user-table! [db] (jdbc/db-do-commands db [user-table-ddl]))

(comment
  (try
    (create-user-table! db)
    (catch java.sql.BatchUpdateException ex
      (log/debug (.getNextException ex) "OK")))

  (misc/drop-table! db "drop table bar"))

(defn find-by-username
  [db username]
  (jdbc/query db ["select password as encrypted-password from users where username = ?" username]))

(defrecord JdbcUserManager [db]
  UserManager
  (add! [this {:keys [:movie/username :movie/password]}]
    (let [password (hashers/encrypt password)]
      (jdbc/insert! db :user {:username username :password password})
      {:movie/username username}))

  (authenticate [this {:keys [:movie/username :movie/password]}]
    (when-let [user (find-by-username db username)]
      (when (hashers/check password (:encrypted-password user))
        (dissoc user :encrypted-password)))))

(comment
  (defmethod user-manager :jdbc
    [config]
    nil))
