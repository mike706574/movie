(ns movie.backend.date
  (:refer-clojure :exclude [format]))

(defn format
  [format date]
  (.format (java.text.SimpleDateFormat. format) date))

(defn parse
  [format string]
  (.parse (java.text.SimpleDateFormat. format) string))

(defn translate
  [src dest string]
  (format dest (parse src string)))
