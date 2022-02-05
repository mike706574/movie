(defproject movie "1.0.0-SNAPSHOT"
  :description "A personal movie app in Clojure/Script"
  :url "http://mike-movie.herokuapp.com"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[aleph "0.4.6"]
                 [buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-hashers "1.8.158"]
                 [com.github.seancorfield/next.jdbc "1.2.731"]
                 [com.stuartsierra/component "1.0.0"]
                 [com.taoensso/timbre "5.1.2"]
                 [metosin/jsonista "0.3.5"]
                 [metosin/muuntaja "0.6.8"]
                 [metosin/reitit "0.5.15"]
                 [migratus "1.3.5"]
                 [org.clojure/clojure "1.11.0-alpha4"]
                 [org.clojure/tools.cli "1.0.206"]
                 [org.postgresql/postgresql "42.3.2"]
                 [selmer "1.12.50"]]
  :min-lein-version "2.0.0"
  :plugins [[lein-shell "0.5.0"]]
  :uberjar-name "movie.jar"
  :profiles {:uberjar {:env {:production true}
                       :aot :all}
             :web {:main movie.backend.main
                   :prep-tasks ["compile" "frontend"]}
             :cli {:main movie.cli.main}
             :dev {:dependencies [[org.clojure/tools.namespace "1.1.0"]]
                   :source-paths ["dev"]}}
  :aliases {"frontend" ["shell" "npx" "shadow-cljs" "release" "frontend"]})
