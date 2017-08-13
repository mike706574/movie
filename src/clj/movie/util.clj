(ns movie.util
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn pretty
  [form]
  (with-out-str (clojure.pprint/pprint form)))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn map-vals
  [f coll]
  (into {} (map (fn [[k v]] [k (f v)]) coll)))

(defmacro log-exceptions
  [message & body]
  `(try
     ~@body
     (catch Exception e#
       (log/error e# ~message)
       (throw e#))))

(defn unkeyword
  [k]
  (cond
    (string? k) k
    (keyword? k) (let [kns (namespace k)
                       kn (name k)]
                   (if kns
                     (str kns "/" kn)
                     kn))
    :else (throw (ex-info (str "Invalid key: " k) {:key k
                                                   :class (class k)}))))

(defn split-on
  [pred coll]
  (reduce
   (fn [[yes no] item]
     (if (pred item)
       (list (conj yes item) no)
       (list yes (conj no item))))
   (list (list) (list))
   coll))

(defn dashed-keyword
  [s]
  (keyword (str/replace s #"_" "-")))

(defn with-retry
  [operation retry? next-wait opts]
  (let [{:keys [initial-wait max-attempts] :or {initial-wait 0}} opts]
    (loop [i 1
           wait initial-wait]
      (let [output (operation)]
        (log/trace (str "Attempt " i " output: " (pretty output)))
        (if (retry? output)
          (if (> i max-attempts)
            (do (log/error (str "Failed after " max-attempts" attempts."))
                (log/error (str "Final output: " output))
                output)
            (do (log/warn (str "Attempt " i " of " max-attempts " failed. Sleeping for " wait " ms."))
                (Thread/sleep wait)
                (recur (inc i) (next-wait wait))))
          output)))))

(defn shorten
  [length string]
  (if (> (count string) length)
    (let [truncated (str/join (take length string))
          last-space-index (.lastIndexOf truncated " ")
          end-index (if (neg? last-space-index)
                      (count truncated)
                      last-space-index)
          spaced (subs truncated 0 end-index)
          end (if (str/ends-with? spaced ".")
                ""
                "...")]
      (str (str/trim spaced) end))))
