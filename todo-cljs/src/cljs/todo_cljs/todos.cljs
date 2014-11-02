(ns todo_cljs.todos
  (:require-macros [cljs.core.async.macros :refer (go)])
  (:require [clojure.browser.dom  :as dom :refer (element)]
            [clojure.browser.event :as ev]
            [clojure.set :as set]
            [cljs.core.async :refer (<! chan)]
            [goog.events :as events]
            [weasel.repl :as ws-repl]))

(enable-console-print!)
(set! dom/log (constantly nil))
(set! dom/log-obj (constantly nil))

;; Constants and State

(def ENTER_KEY 13)
(def STORAGE_NAME "todos-cljs")
(def todo-list (atom [])) ;; ALL APPLICATION STATE LIVES HERE

(declare rerender)

(add-watch todo-list ::rerender
           (fn [k a o n]
             (rerender o n)))

;; State management

(defn save-todos []
  (.setItem js/localStorage STORAGE_NAME
            (pr-str @todo-list)))

(defn load-todos []
  (let [local-storage (.getItem js/localStorage STORAGE_NAME)]
    (if (empty? local-storage)
      (reset! todo-list [])
      (reset! todo-list
              (cljs.reader/read-string local-storage)))))

;; HELPER: shortcut for dom/get-element
(defn by-id [id] (dom/get-element id))

;; HELPER: :total tasks, :completed tasks and :left tasks (not completed)
(defn stats []
  (let [total     (count @todo-list)
        completed (count (filter #(= true (% :completed)) @todo-list))
        left      (- total completed)]
    {:total total :completed completed :left left}))

;; HELPER: updates a todo by its id, changes puts a new val for the attr
(defn update-attr [id attr val]
  (let [updated
        (vec (map #(if (= (% :id) id) (conj % {attr val}) %) @todo-list))]
    (reset! todo-list updated)))

(defn remove-todo-by-id [id]
  (reset! todo-list
          (vec (filter #(not= (% :id) id) @todo-list))))

;; UI and handlers

(defn delete-click-handler [ev]
  (let [id (.getAttribute (.-target ev) "data-todo-id")]
    (remove-todo-by-id id)))

(defn checkbox-change-handler [ev]
  (let [checkbox (.-target ev)
        id       (.getAttribute checkbox "data-todo-id")
        checked  (.-checked checkbox)]
    (update-attr id :completed checked)))

(defn todo-content-handler [ev]
  (let [id    (.getAttribute (.-target ev) "data-todo-id")
        div   (by-id (str "li_" id))
        input (by-id (str "input_" id))]
    (dom/set-properties div {"class" "editing"})
    (.focus input)))

(defn input-todo-key-handler [ev]
  (let [input (.-target ev)
        text  (.trim (.-value input))
        id    (apply str (drop 6 (.-id input)))]
    (if (seq text)
      (if (= ENTER_KEY (.-keyCode ev))
        (do
          (update-attr id :title text)))
      (do
        (remove-todo-by-id id)))))

(defn input-todo-blur-handler [ev]
  (let [input (.-target ev)
        text  (.trim (.-value input))
        id    (apply str (drop 6 (.-id input)))] ;; drops "input_"
    (do
      (update-attr id :title text))))

(defn redraw-todos-ui []
  (dom/remove-children "todo-list")
  (dom/set-value (by-id "new-todo") "")
  (doseq [todo @todo-list]
      (let [
        id          (todo :id)
        li          (element "li" {:id (str "li_" id)})
        checkbox    (element "input" {"class" "toggle" "data-todo-id" id
                                   "type" "checkbox"})
        label       (element "label" {"data-todo-id" id})
        delete-link (element "button" {"class" "destroy" "data-todo-id" id})
        div-display (element "div" {"class" "view" "data-todo-id" id})
        input-todo  (element "input" {:id (str "input_" id) "class" "edit"})]

        (dom/set-text label (todo :title))
        (dom/set-value input-todo (todo :title))

        (ev/listen checkbox "change" checkbox-change-handler)
        (ev/listen label "dblclick" todo-content-handler)
        (ev/listen delete-link "click" delete-click-handler)
        (ev/listen input-todo "keypress" input-todo-key-handler)
        (ev/listen input-todo "blur" input-todo-blur-handler)

        (dom/append div-display checkbox label delete-link)
        (dom/append li div-display input-todo)

        (if (todo :completed)
          (do
            (dom/set-properties li {"class" "complete"})
            (dom/set-properties checkbox {"checked" true})))

        (dom/append (by-id "todo-list") li))))

(defn draw-todo-count []
  (let [stat (stats)
        text (str " " (if (= 1 (:left stat)) "item" "items") " left")
        number (dom/element "strong" (str (:left stat)))
        remaining (dom/element "span" {:id "todo-count"})
        footer (by-id "footer")]
    (dom/append remaining number text)
    (dom/append footer remaining)))

(defn clear-click-handler []
  (reset! todo-list (filter #(not (% :completed)) @todo-list)))

(defn draw-todo-clear []
  (let [footer (by-id "footer")
        message (str "Clear completed (" (:completed (stats)) ")")
        button (dom/element "button" {:id "clear-completed"} message)]
    (ev/listen button "click" clear-click-handler)
    (dom/append footer button)))

(defn redraw-status-ui []
  (let [footer  (by-id "footer")
        display (if (empty? @todo-list) "none" "block")
        stat (stats)]
    (dom/remove-children "footer")
    (dom/set-properties footer {"style" (str "display:" display)})
    (if (not= 0 (:completed stat)) (draw-todo-clear))
    (if (not= 0 (:total stat)) (draw-todo-count))))

(defn change-toggle-all-checkbox-state []
  (let [toggle-all  (by-id "toggle-all")
        all-checked (every? #(= true (% :completed)) @todo-list)]
    (set! (.-checked toggle-all) all-checked)))

(defn rerender [o n]
  ;; calculate some diff between old and new and do clever update...
  (let [diff-o (set/difference (set o) (set n))
        diff-n (set/difference (set n) (set o))]
    (println "diff-o" diff-o)
    (println "diff-n" diff-n)
    ;; for now just naive rerendering
    (when (not= o n)
      (save-todos)
      (redraw-todos-ui)
      (redraw-status-ui)
      (change-toggle-all-checkbox-state))))

;; This get-uuid fn is almost equiv to the original
(defn get-uuid []
  (apply
   str
   (map
    (fn [x]
      (if (= x \0)
        (.toString (bit-or (* 16 (.random js/Math)) 0) 16)
        x))
    "00000000-0000-4000-0000-000000000000")))

(defn add-todo [text]
  (let [tt (.trim text)]
    (if (seq tt)
      (swap! todo-list conj
             {:id (get-uuid)
              :title tt
              :completed false}))))

(defn new-todo-handler [ev]
  (if (= ENTER_KEY (.-keyCode ev))
    (add-todo (.-value (by-id "new-todo")))))

(defn toggle-all-handler [ev]
  (let [checked (.-checked (.-target ev))
        toggled (map #(assoc % :completed checked) @todo-list)]
    (reset! todo-list toggled)))

(defn add-event-listeners []
  (ev/listen (by-id "new-todo") "keypress" new-todo-handler)
  (ev/listen (by-id "toggle-all") "change" toggle-all-handler)
)

(defn window-load-handler []
  (load-todos)
  (add-event-listeners))

;; Launch window-load-handler when window loads
;; -- not sure why (ev/listen js/window "load" fn) does not work
(events/listen js/window "load" window-load-handler)

;; To connect a browser-attached repl:
;; (repl/connect "http://localhost:9000/repl")
(ws-repl/connect "ws://localhost:9005" :verbose true)

;; Debugging:
;; (in-ns 'todo-cljs.todos)
;; (add-todo "one")
;; (add-todo "two")
;; (add-todo "three")
;; (map #(js/alert %) @todo-list)

