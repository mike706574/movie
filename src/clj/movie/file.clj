(ns movie.file
  (:refer-clojure :exclude [load])
  (:require [movie.io :as io]
            [movie.util :as util]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(def alphabet ["a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z"])

(defn file-type?
  [exts file]
  (boolean (some #(.endsWith (io/absolute file) %) exts)))

(def video-exts [".mkv" ".avi" ".mp4" ".m4v" ".wmv"])
(def video? (partial file-type? video-exts))

(def subtitles-exts [".srt"])
(def subtitles? (partial file-type? subtitles-exts))

(defn classify-file
  [file]
  (cond
    (video? file) :video
    (subtitles? file) :subtitle
    :else :other))

(defn parse-item
  [dir]
  (let [{:keys [video subtitle unknown]} (group-by classify-file (io/list dir))]
    {:title (io/base dir)
     :directory (io/absolute dir)
     :videos (mapv io/name video)
     :subtitles (mapv io/name subtitle)}))

(defn parse-letter-dir
  [path letter]
  (let [path (str path "/" letter)]
    (if (io/exists? path)
      (->> (io/list path)
           (filter io/directory?)
           (map parse-item)
           (map #(assoc % :letter letter)))
      [])))

(defn parse-category-dir
  [path]
  (mapcat #(parse-letter-dir path %) alphabet))

(defn load
  [root]
  (mapcat #(parse-category-dir (str root %)) ["/unwatched" "/watched"]))
