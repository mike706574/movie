(ns movie.io
  (:refer-clojure :exclude [list name])
  (:require [clojure.java.io :as io]
            [potemkin :as potemkin]))

(potemkin/import-vars
  [clojure.java.io
    file
    copy
    make-parents])

(defn delete
  [arg]
  (io/delete-file arg))

(defn delete-recursively
  [arg]
  (doseq [child (reverse (file-seq (file file)))]
    (delete child)))

(defn make-directory
  [arg]
  (.mkdir (file arg)))

(defn touch
  [file]
  (make-parents file)
  (.createNewFile (file file)))

(defn name
  [arg]
  (.getName (file arg)))

(defn base
  [arg]
  (let [name (.getName (file arg))
        dot-index (.lastIndexOf name ".")]
    (if (pos? dot-index) (subs name 0 dot-index) name)))

(defn extension
  [arg]
  (let [name (.getName (file arg))
        dot-index (.lastIndexOf name ".")]
    (when (pos? dot-index)
      (subs name dot-index))))

(defn absolute
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

(defn exists?
  [arg]
  (.exists (file arg)))

(defn list
  [arg]
  (.listFiles (file arg)))

(defn write-edn
  [path data]
  (with-open [writer (io/writer path)]
    (print-method data writer)))

(defn read-edn
  [path]
  (with-open [reader (java.io.PushbackReader.
                      (io/reader path))]
    (binding [*read-eval* false]
      (read reader))))

(defn read-edn-resource
  [path]
  (with-open [reader (-> path
                         (io/resource)
                         (io/reader)
                         (java.io.PushbackReader.))]
    (binding [*read-eval* false]
      (read reader))))
