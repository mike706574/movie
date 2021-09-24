(ns movie.frontend.alphabet
  (:refer-clojure :exclude [next])
  (:require [clojure.string :as str]))

(def ^:private alphabet-map {"A" 0 "B" 1 "C" 2 "D" 3 "E" 4 "F" 5 "G" 6 "H" 7 "I" 8 "J" 9 "K" 10 "L" 11 "M" 12 "N" 13 "O" 14 "P" 15 "Q" 16 "R" 17 "S" 18 "T" 19 "U" 20 "V" 21 "W" 22 "X" 23 "Y" 24 "Z" 25})
(def alphabet-vector ["A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"])

(defn next
  [letter]
  (get
   alphabet-vector
   (mod
    (inc (get alphabet-map letter))
    26)))

(defn previous
  [letter]
  (get
   alphabet-vector
   (mod
    (dec (get alphabet-map letter))
    26)))

(defn take-after
  [n letter]
  (let [index (get alphabet-map letter)
        lo (inc index)
        hi (+ lo n)
        indices (range lo hi)]
    (map #(get alphabet-vector (mod % 26)) indices)))

(defn take-before
  [n letter]
  (let [index (get alphabet-map letter)
        lo (- index n)
        indices (range lo index)]
    (map #(get alphabet-vector (mod % 26)) indices)))
