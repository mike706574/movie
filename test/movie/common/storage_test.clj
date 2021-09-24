(ns movie.common.storage-test
  (:require [clojure.test :refer :all]
            [clojure.zip :as zip]
            [movie.common.storage :as storage]))

(deftest file-type?
  (is (true? (storage/file-type? [".txt"] "foo.txt")))
  (is (false? (storage/file-type? [".txt"] "foo.gif")))
  (is (true? (storage/file-type? [".txt" ".gif"] "foo.gif"))))

(deftest classify-file?
  (is (= :movie (storage/classify-file "foo.avi")))
  (is (= :subtitles (storage/classify-file "foo.srt")))
  (is (= :other (storage/classify-file "foo.gif"))))
