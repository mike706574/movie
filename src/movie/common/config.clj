(ns movie.common.config
  (:require [environ.core :as environ]))

(def envs #{"dev" "prod"})

(defn get-env []
  (let [env (or (environ/env :env) "dev")]
    (when-not (envs env)
      (throw (ex-info "Invalid env" {:env env})))
    env))
