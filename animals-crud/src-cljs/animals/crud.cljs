(ns animals.crud
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
          (println "dev mode is on")
          #_(fw/watch-and-reload
           :websocket-url   "ws://localhost:3449/figwheel-ws"
           :jsload-callback
           (fn []
             (println "reloaded")))
          #_(ws-repl/connect "ws://localhost:9001" :verbose true)))))

(defonce animals-state (atom #{}))

;; fire off go loop only once
(go (let [response
          (<! (http/get "/animals"))
          data (:body response)]
      (reset! animals-state (set data))))

;;; crud operations
(defn add-animal! [a]
  (go (let [response
            (<! (http/post "/animals" {:edn-params
                                        a}))]
        (swap! animals-state conj (:body response)))))

(defn remove-animal! [a]
  (go (let [response
            (<! (http/delete (str "/animals/"
                                  (:id a))))]
        (if (= (:status response)
                 200)
          (swap! animals-state disj a)))))

(defn update-animal! [a]
  (go (let [response
            (<! (http/put (str "/animals/" (:id a))
                          {:edn-params a}))
            updated-animal (:body response)]
        (swap! animals-state
               (fn [old-state]
                 (conj
                   (set (filter (fn [other]
                                  (not= (:id other)
                                        (:id a)))
                                old-state))
                   updated-animal))))))
;;; end crud operations

(defn field-input-handler
  "Returns a handler that updates value in atom map,
  under key, with value from onChange event"
  [atom key]
  (fn [e]
    (swap! atom
           assoc key
           (.. e -target -value))))

(defn input-valid? [atom]
  (and (seq (-> @atom :name))
       (seq (-> @atom :species))))

(defn editable-input [atom key]
  (if (:editing? @atom)
    [:input {:type     "text"
             :value    (get @atom key)
             :onChange (field-input-handler atom key)}]
    [:p (get @atom key)]))

(def editable-input (with-meta editable-input
                      {:component-did-mount (fn [this] (println "will mount"))
                       :component-will-unmount (fn [this] (println "unmount"))}))

(defn animal-row [a]
  (let [row-state (atom {:editing? false
                         :name     (:name a)
                         :species  (:species a)})
        current-animal (fn []
                         (assoc a
                           :name (:name @row-state)
                           :species (:species @row-state)))]
    (fn []
      [:tr
       [:td [editable-input row-state :name]]
       [:td [editable-input row-state :species]]
       [:td [:button.btn.btn-primary.pull-right
             {:disabled (not (input-valid? row-state))
              :onClick (fn []
                         (when (:editing? @row-state)
                           (update-animal! (current-animal)))
                         (swap! row-state update-in [:editing?] not))}
             (if (:editing? @row-state) "Save" "Edit")]]
       [:td [:button.btn.pull-right.btn-danger
             {:onClick #(remove-animal! (current-animal))}
             "\u00D7"]]])))

(defn animal-form []
  (let [initial-form-values {:name     ""
                             :species  ""
                             :editing? true}
        form-input-state (atom initial-form-values)]
    (fn []
      [:tr
       [:td [editable-input form-input-state :name]]
       [:td [editable-input form-input-state :species]]
       [:td [:button.btn.btn-primary.pull-right
             {:disabled (not (input-valid? form-input-state))
              :onClick  (fn []
                          (add-animal! @form-input-state)
                          (reset! form-input-state initial-form-values))}
             "Add"]]])))

(defn animals []
  [:div
   [:table.table.table-striped
    [:thead
     [:tr
      [:th "Name"] [:th "Species"] [:th ""] [:th ""]]]
    [:tbody
     (map (fn [a]
            ^{:key (str "animal-row-" (:id a))}
            [animal-row a])
          (sort-by :name @animals-state))
     [animal-form]]]])


(reagent/render-component [animals]
                          (js/document.getElementById "app"))
