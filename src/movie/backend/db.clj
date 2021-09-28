(ns movie.backend.db
  (:require [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn- index-by
  "Returns a map in which values are items of coll and keys are the result of applying f to each item."
  [f coll]
  (into {} (map (juxt f identity)) coll))

(defn new-db
  "Given a database spec, returns a DataSource using our custom options."
  [spec]
  (let [db (-> spec
               (jdbc/get-datasource)
               (jdbc/with-options {:builder-fn rs/as-unqualified-maps}))]
    (if (:log? spec)
      (jdbc/with-logging db
        (fn [sym sql-params]
          (prn sym sql-params)
          (System/currentTimeMillis))
        (fn [sym state result]
          (prn sym
               (- (System/currentTimeMillis) state)
               (cond
                 (map? result) result
                 (sequential? result) (count result)
                 :else result))))
      db)))

(defn new-pg-db
  "Given a partial database spec, most likely returns a PostgreSQL dataSource
  with custom options and some defaults for local development."
  [spec]
  (new-db (merge {:dbtype "postgresql"
                  :dbname "postgres"
                  :classname "org.postgresql.Driver"
                  :subprotocol "postgres"
                  :host "localhost"
                  :port 5432
                  :user "postgres"
                  :password "postgres"}
                 spec)))

(defn- new-migratus-config [db]
  {:store :database
   :migration-dir "migrations"
   :db (jdbc/get-datasource db)})

(defn init [db]
  (migratus/init (new-migratus-config db)))

(defn migrate [db]
  (migratus/migrate (new-migratus-config db)))

(defn rollback [db]
  (migratus/rollback (new-migratus-config db)))

(defn reset [db]
  (migratus/reset (new-migratus-config db)))

(defn up [db id]
  (migratus/up (new-migratus-config db) id))

(defn down [db id]
  (migratus/down (new-migratus-config db) id))

(defn migrations [db]
  (jdbc/execute! db ["SELECT * FROM schema_migrations"]))

(def schemas-sql "SELECT nspname AS name
FROM pg_catalog.pg_namespace
WHERE nspname !~ '^pg_' AND nspname <> 'information_schema'")

(defn list-schema-names
  "Returns a set containing the names of all non-system schemas in a database."
  [db]
  (set (map :name (jdbc/execute! db [schemas-sql]))))

(defn schema-exists?
  "Returns true if a non-system schema with the given name exists; otherwise, false."
  [db name]
  (contains? (list-schema-names db) name))

(def tables-sql "SELECT table_schema AS schema,
  table_name AS name
FROM information_schema.tables
WHERE table_schema !~ '^pg_' AND table_schema <> 'information_schema'")

(defn list-tables
  "Returns a set containing the table key of all non-system tables in a database. A table key is a map containing the keys :schema and :name."
  [db]
  (set (jdbc/execute! db [tables-sql])))

(defn table-exists?
  "Returns true if a non-system table with the table key exists in a database. A table key is a map containing the keys :schema and :name."
  [db key]
  (contains? (list-tables db) key))

(def columns-sql "SELECT table_schema AS schema,
  table_name AS table,
  column_name AS name,
  data_type AS type
FROM information_schema.columns
WHERE table_schema !~ '^pg_' AND table_schema <> 'information_schema'")

(defn list-columns
  "Returns a set containing a column map for each non-system column in a database. A column map contains the keys :schema, :table, :name, and :type."
  [db]
  (set (jdbc/execute! db [columns-sql])))

(def pk-columns-sql "SELECT kcu.table_schema AS schema,
  kcu.table_name AS table,
  kcu.column_name AS name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu USING (constraint_name, constraint_schema)
WHERE tc.constraint_type = 'PRIMARY KEY' AND kcu.table_schema !~ '^pg_' AND kcu.table_schema <> 'information_schema'")

(defn list-pk-columns
  "Returns a set containing a PK column map for each non-system primary key column in a database. A PK column map contains the keys :schema, :table, and :name."
  [db]
  (set (jdbc/execute! db [pk-columns-sql])))

(defn column-table-key
  "Returns the table key derived from the given column map. A table key is a map containing the keys :schema and :name."
  [column]
  (let [{:keys [schema table]} column]
    {:schema schema :name table}))

(defn collect-tables
  "Returns a set containing table maps for all non-system tables in a database. The table map contains the keys :schema, :name, :columns, and :pk-column."
  [db]
  (let [tables (list-tables db)
        pk-columns (list-pk-columns db)
        pk-column-lookup (index-by column-table-key pk-columns)
        columns (list-columns db)
        columns-lookup (group-by column-table-key columns)]
    (->> tables
         (map
          (fn [table]
            (let [table-key (select-keys table [:schema :name])]
              (assoc table
                     :columns (into #{} (get columns-lookup table-key))
                     :pk-column (get pk-column-lookup table-key)))))
         set)))
