(ns reagent-counter.main
  (:require-macros [cljs.core.async.macros :refer (go)])
  (:require
    [reagent.core :as reagent :refer [atom]]
    [cljs-http.client :as http]
    [cljs.core.async :refer (<!)]
    [figwheel.client :as fw]
    [weasel.repl :as ws-repl]))

(enable-console-print!)

(defonce init-dev
  ;; we need to defonce this, so it won't be executed again upon code
  ;; reload
  (go (let [body (:body (<! (http/get "/is-dev")))]
        (when (= body true) ;; has to match exactly true and not some string
          (fw/watch-and-reload
           :websocket-url   "ws://localhost:3449/figwheel-ws"
           :jsload-callback
           (fn []
             (println "reloaded")))
          (ws-repl/connect "ws://localhost:9001" :verbose true)))))

(comment
  (def count-state (atom 10))

  (defn counter []
    [:div
     @count-state
     [:button {:on-click #(swap! count-state inc)}
      "x"]]))

(comment
  (defn local-counter [start-value]
    (let [count-state (atom start-value)]
      (fn []
        [:div
         @count-state
         [:button {:on-click #(swap! count-state inc)}
          "x"]])))

  (reagent/render-component [local-counter 10]
                            (js/document.getElementById "app")))

(def time-state (atom {:day "Thursday"}))

(def component (with-meta
                (fn [x]
                  [:p "Hello " x ", it is " (:day @time-state)])
                {:component-will-mount #(println "called before mounting")
                 :component-did-update #(js/alert "called after updating")} ))


(reagent/render-component [component "Michiel"]
                          (js/document.getElementById "app"))
