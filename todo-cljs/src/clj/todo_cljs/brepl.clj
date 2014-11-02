(ns todo-cljs.brepl
  (:require
   [cemerick.piggieback :as piggieback]
   [weasel.repl.websocket :as weasel]))

(defn brepl []
  (piggieback/cljs-repl
   :repl-env
   (weasel/repl-env :ip "0.0.0.0" :port 9005)))
