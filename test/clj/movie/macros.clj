(ns movie.macros
  (:require [com.stuartsierra.component :as component]))

(defmacro with-component
  [constructor config & body]
  `(let [~'component (component/start (~constructor ~config))]
     (try
       ~@body
       (finally (component/stop ~'component)))))

(defmacro with-system
  [system-map & body]
  `(let [~'system (component/start-system ~system-map)]
     (try
       ~@body
       ~'system
       (finally (component/stop-system ~'system)))))
