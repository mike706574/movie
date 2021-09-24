(ns movie.common.json
  (:require [jsonista.core :as json]
            [movie.common.util :as util]))

(def ^:private json-mapper
  (json/object-mapper
   {:decode-key-fn util/dashed-keyword}))

(defn read-value [val]
  (json/read-value val json-mapper))

(defn write-value-as-string [val]
  (json/write-value-as-string val json-mapper))
