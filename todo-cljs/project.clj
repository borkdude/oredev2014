(defproject todo-cljs "0.1.0-SNAPSHOT"
  :description "A port of the Todos JavaScript app to ClojureScript"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :source-paths ["src/clj"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2342"]
                 [weasel "0.4.2-SNAPSHOT"]
                 [com.cemerick/piggieback "0.1.3"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]]

  :profiles {:dev {:repl-options {:init-ns todo-cljs.brepl
                                  :nrepl-middleware
                                  [cemerick.piggieback/wrap-cljs-repl]}}}

  ;; cljsbuild configuration based on https://github.com/magomimmo/modern-cljs/
  :cljsbuild
  {:builds
   [{:source-paths ["src/cljs"],
     :id "dev",
     :compiler
     {:pretty-print true,
      :output-to "resources/public/js/todos.js",
      :output-dir "resources/public/js/out",
      :optimizations :none,
      :source-map true}}
    {:source-paths ["src/cljs"],
     :id "prod",
     :compiler
     {:output-to "resources/public/js/todos_prod.js",
      :optimizations :advanced}}]}
  )
