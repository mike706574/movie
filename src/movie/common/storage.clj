(ns movie.common.storage
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [movie.common.json :as json]
            [taoensso.timbre :as log]))

(def file clojure.java.io/file)

(defn delete-file!
  [arg]
  (io/delete-file arg))

(defn delete-file-recursively!
  [arg]
  (let [f (file arg)]
    (when (.exists f)
      (when (.isDirectory f)
        (doseq [file-in-dir (.listFiles f)]
          (delete-file-recursively! file-in-dir)))
      (delete-file! f))))

(defn make-directory!
  [arg]
  (.mkdir (file arg)))

(defn touch-file!
  [arg]
  (io/make-parents arg)
  (.createNewFile (file arg)))

(defn file-name
  [arg]
  (.getName (file arg)))

(defn file-base
  [arg]
  (let [name (.getName (file arg))
        dot-index (.lastIndexOf name ".")]
    (if (pos? dot-index) (subs name 0 dot-index) name)))

(defn file-extension
  [arg]
  (let [name (.getName (file arg))
        dot-index (.lastIndexOf name ".")]
    (when (pos? dot-index)
      (subs name dot-index))))

(defn file-absolute-path
  [arg]
  (.getAbsolutePath (file arg)))

(defn parent
  [arg]
  (.getParent (file arg)))

(defn file?
  [arg]
  (.isFile (file arg)))

(defn directory?
  [arg]
  (.isDirectory (file arg)))

(defn file-exists?
  [arg]
  (.exists (file arg)))

(defn list-files
  [arg]
  (.listFiles (file arg)))

(def alphabet ["A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"])

(defn file-type?
  [exts file]
  {:pre [(seq exts)
         (every? string? exts)]}
  (let [path (file-absolute-path file)]
    (boolean (some (fn [ext] (.endsWith path ext)) exts))))

(def video-exts [".mkv" ".avi" ".mp4" ".m4v" ".wmv"])
(def video-file? (partial file-type? video-exts))

(def subtitle-exts [".srt"])
(def subtitle-file? (partial file-type? subtitle-exts))

(defn classify-file
  [file]
  (cond
    (video-file? file) :video
    (subtitle-file? file) :subtitles
    :else :other))

(defn metadata-path [path] (str path "/metadata.json"))

(defn read-movie-dir
  [dir]
  (let [{:keys [path letter]} dir
        {:keys [video subtitle other]} (group-by classify-file (list-files path))
        metadata-path (metadata-path path)
        {:keys [uuid]} (when (file-exists? metadata-path)
                         (json/read-value (slurp metadata-path)))]
    {:uuid uuid
     :letter letter
     :path (file-absolute-path path)
     :title (file-base path)
     :video-files (mapv file-name video)
     :subtitle-files (mapv file-name subtitle)
     :other-files (mapv file-name other)}))

(defn letter-path [path letter] (str path "/" (str/lower-case letter)))

(defn letter-movie-dirs [path letter]
  (let [letter-path (letter-path path letter)]
    (if (file-exists? path)
      (->> letter-path
           (list-files)
           (filter directory?)
           (map file-absolute-path)
           (map #(hash-map :letter letter :path %)))
      [])))

(defn movie-dirs [path]
  (->> alphabet
       (map letter-movie-dirs)
       (flatten)))

(defn read-letter-dir
  [path letter]
  (->> (letter-movie-dirs path letter)
       (map read-movie-dir)))

(defn read-movies-dir
  [path]
  (->> alphabet
       (map (partial read-letter-dir path))
       (flatten)
       (into [])))

(defn write-metadata! [path metadata]
  (spit (metadata-path path) (json/write-value-as-string metadata)))

(defn mock-movie!
  [path movie]
  (let [{:keys [letter title video-files]} movie
        letter-path (letter-path path letter)
        movie-dir (str letter-path "/" title)]
    (when-not (file-exists? letter-path)
      (make-directory! letter-path))
    (make-directory! movie-dir)
    (doseq [video-file video-files]
      (let [video-file-path (str movie-dir "/" video-file)]
        (spit video-file-path video-file)))))

(defn mock-dir!
  [path movies]
  (let [letter-movies (group-by :letter movies)]
    (delete-file-recursively! path)
    (make-directory! path)
    (doseq [[letter movies] letter-movies]
      (let [letter-path (letter-path path letter)]
        (make-directory! letter-path)
        (doseq [movie movies]
          (println "MOVIE" movie)
          (mock-movie! path (assoc movie :letter letter)))))))
