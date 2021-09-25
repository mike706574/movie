(ns movie.common.json
  (:require [clojure.string :as str]
            [jsonista.core :as json]))

(defn- dashed [x] (keyword (str/replace (name x) #"_" "-")))

(def ^:private json-mapper
  (json/object-mapper
   {:decode-key-fn dashed}))

(defn read-value [val]
  (json/read-value val json-mapper))

(defn write-value-as-string [val]
  (json/write-value-as-string val json-mapper))
