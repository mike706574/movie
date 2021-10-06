(ns movie.frontend.util)

(defn includes-ignore-case?
  [string sub]
  (not (nil? (.match string (re-pattern (str "(?i)" sub))))))

(defn classes [entries]
  (->> entries
       (filter (fn [entry]
                 (or (string? entry)
                     (= (count entry) 1)
                     (second entry))))
       (map (fn [entry]
              (if (string? entry)
                entry
                (first entry))))))
