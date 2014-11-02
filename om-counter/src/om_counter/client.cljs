(ns om-counter.client
    (:require-macros [cljs.core.async.macros :refer (go)])
    (:require [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [cljs.core.async :refer [<! timeout chan put!]]
              [cljs-http.client :as http]))

(enable-console-print!)

(def app-state (atom {:counter 0}))

(defn app-state-counter [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (:counter app)
               (dom/button
                #js
                {:onClick
                 #(om/transact! app :counter inc)}
                "x")))))

(defn local-state-counter [_ owner {:keys [start-value]}]
  (println start-value)
  (reify
    om/IInitState
    (init-state [_]
      {:counter start-value})
    om/IRender
    (render [_]
      (dom/div nil
               (om/get-state owner :counter)
               (dom/button
                #js
                {:onClick #(om/update-state! owner :counter inc)}
                "x")))))

(comment
  (def history (atom []))

  (defn app-state-counter-with-undo [app owner]
    (reify
      om/IRender
      (render [_]
        (dom/div nil
                 (:counter app)
                 (dom/button
                  #js
                  {:onClick (fn []
                              (swap! history conj @app)
                              (om/transact! app :counter inc))} "x")
                 (dom/button
                  #js
                  {:onClick (fn []
                              (when (seq @history)
                                (om/update! app (last @history))
                                (swap! history pop)))} "undo"))))))

(def event-chan (chan))

(go (loop []
      (put! event-chan :click)
      (<! (timeout 1000))
      (recur)))

(defn channel-counter [_ owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go (loop []
            (when-let [msg (<! (om/get-state owner :chan))]
              (om/update-state! owner :counter inc)
              (recur)))))
    om/IRender
    (render [_]
      (dom/div nil
               (om/get-state owner :counter)))))

(om/root
 (fn [app owner]
   (om/component
    (om/build channel-counter nil {:init-state {:chan event-chan
                                                :counter 10}})))
 app-state
 {:target (. js/document (getElementById "app"))})


(def app-state {:counter1 {:count 10}
                :counter2 {:count 11}})

(defn main [app owner]
  (om/component
   (dom/div nil
            (om/build counter (:counter1 app))
            (om/build counter (:counter2 app)))))
