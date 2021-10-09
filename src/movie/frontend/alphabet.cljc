(ns movie.frontend.alphabet
  (:refer-clojure :exclude [next]))

(def alphabet-vector ["a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z" "#"])

(def alphabet-map (->> alphabet-vector
                       (map-indexed (fn [idx item] [item idx]))
                       (into {})))

(def alphabet-length (count alphabet-vector))

(defn next [letter]
  (get
   alphabet-vector
   (mod
    (inc (get alphabet-map letter))
    alphabet-length)))

(defn previous [letter]
  (get
   alphabet-vector
   (mod
    (dec (get alphabet-map letter))
    alphabet-length)))

(defn take-after [n letter]
  (let [index (get alphabet-map letter)
        lo (inc index)
        hi (+ lo n)
        indices (range lo hi)]
    (map #(get alphabet-vector (mod % alphabet-length)) indices)))

(defn take-before [n letter]
  (let [index (get alphabet-map letter)
        lo (- index n)
        indices (range lo index)]
    (map #(get alphabet-vector (mod % alphabet-length)) indices)))
