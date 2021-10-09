(ns movie.common.storage
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [movie.common.json :as json]
            [taoensso.timbre :as log]))

(def file clojure.java.io/file)

(defn sep [] (java.io.File/separator))

(defn delete-file! [arg]
  (io/delete-file arg))

(defn delete-file-recursively! [arg]
  (let [f (file arg)]
    (when (.exists f)
      (when (.isDirectory f)
        (doseq [file-in-dir (.listFiles f)]
          (delete-file-recursively! file-in-dir)))
      (delete-file! f))))

(defn make-directory! [arg]
  (.mkdir (file arg)))

(defn make-directory-with-parents! [arg]
  (io/make-parents arg)
  (make-directory! arg))

(defn touch-file! [arg]
  (io/make-parents arg)
  (.createNewFile (file arg)))

(defn file-name [arg]
  (.getName (file arg)))

(defn file-base [arg]
  (let [name (.getName (file arg))
        dot-index (.lastIndexOf name ".")]
    (if (pos? dot-index) (subs name 0 dot-index) name)))

(defn file-extension [arg]
  (let [name (.getName (file arg))
        dot-index (.lastIndexOf name ".")]
    (when (pos? dot-index)
      (subs name dot-index))))

(defn file-absolute-path [arg]
  (.getAbsolutePath (file arg)))

(defn parent [arg]
  (.getParent (file arg)))

(defn file? [arg]
  (.isFile (file arg)))

(defn directory? [arg]
  (.isDirectory (file arg)))

(defn file-exists? [arg]
  (.exists (file arg)))

(defn list-files [arg]
  (.listFiles (file arg)))

(defn file-type? [exts file]
  {:pre [(seq exts)
         (every? string? exts)]}
  (let [path (file-absolute-path file)]
    (boolean (some (fn [ext] (.endsWith path ext)) exts))))

(def video-exts [".mkv" ".avi" ".mp4" ".m4v" ".wmv"])
(def video-file? (partial file-type? video-exts))

(def subtitle-exts [".srt"])
(def subtitle-file? (partial file-type? subtitle-exts))

(defn classify-file [file]
  (cond
    (video-file? file) :video
    (subtitle-file? file) :subtitles
    :else :other))

(defn metadata-path [path]
  (str path (sep) "metadata.json"))

(defn read-movie-dir [dir]
  (let [{:keys [path category]} dir
        {:keys [video subtitle other]} (group-by classify-file (list-files path))
        metadata-path (metadata-path path)
        metadata (when (file-exists? metadata-path)
                   (json/read-value (slurp metadata-path)))]
    (merge
     {:category category
      :path (file-absolute-path path)
      :title (file-base path)
      :video-files (mapv file-name video)
      :subtitle-files (mapv file-name subtitle)
      :other-files (mapv file-name other)}
     metadata)))

(defn category-path [path category]
  (str path (sep) (str/lower-case category)))

(defn category-movie-dirs [path category]
  (if (file-exists? path)
    (->> path
         (list-files)
         (filter directory?)
         (map file-absolute-path)
         (map #(hash-map :category category :path %)))
    []))

(defn read-category-dir [path category]
  (->> (category-movie-dirs path category)
       (map read-movie-dir)))

(defn read-root-dir [path]
  (->> path
       (list-files)
       (filter directory?)
       (mapcat #(read-category-dir (file-absolute-path %) (file-base %)))
       (into [])))

(defn write-metadata! [path metadata]
  (spit (metadata-path path) (json/write-value-as-string metadata)))

(defn mock-movie-dir! [root movie]
  (let [{:keys [path title video-files]} movie
        movie-dir (str root (sep) path (sep) title)]
    (make-directory-with-parents! movie-dir)
    (doseq [video-file video-files]
      (let [video-file-path (str movie-dir (sep) video-file)]
        (spit video-file-path video-file)))))

(defn mock-movie-dirs! [root movies]
  (delete-file-recursively! root)
  (doseq [movie movies] (mock-movie-dir! root movie)))
