(ns movie.common.config)

(def envs #{"dev" "prod"})

(defn get-env-var [name]
  (System/getenv name))

(defn get-env []
  (let [env (or (get-env-var "ENV") "dev")]
    (when-not (envs env)
      (throw (ex-info "Invalid env" {:env env})))
    env))
