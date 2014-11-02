(ns animals.api
    (:require
     [liberator.core :refer (resource)]
     [compojure.core :refer (defroutes ANY GET)]
     [compojure.route :refer (resources not-found)]
     [ring.middleware.params :refer (wrap-params)]
     [ring.middleware.edn :refer (wrap-edn-params)]
     [ring.util.response :refer (redirect)]
     [animals.animals :as animals]
     [clj-json.core :as json]
     [cemerick.piggieback :as piggieback]
     [weasel.repl.websocket :as weasel]
     [environ.core :refer [env]]))

(def is-dev? (let [setting (env :is-dev)]
               (if (string? setting)
                 (Boolean/parseBoolean setting)
                 setting)))

(defn handle-exception
  [ctx]
  (let [e (:exception ctx)]
    (.printStackTrace e)
    {:status 500 :message (.getMessage e)}))

(defroutes routes
  (ANY "/animals"
       [name species]
       (resource
        :available-media-types ["application/edn"]
        :allowed-methods [:get :post]
        :handle-ok (fn [ctx]
                     (let [found (animals/read)]
                       (condp = (-> ctx :representation :media-type)
                         "application/edn" found
                         "application/json" (json/generate-string found))))
        :post! (fn [ctx] {::id (animals/create! {:name name :species species})})
        :post-redirect? (fn [ctx] {:location (str "/animals/" (::id ctx))})
        :handle-exception handle-exception))
  (ANY "/animals/:id"
       [id name species]
       (let [id (Integer/parseInt id)]
         (resource
           :available-media-types ["application/edn"]
           :allowed-methods [:get :put :delete]
           :handle-ok (fn [ctx]
                        (animals/read id))
           :put! (fn [ctx]
                   (animals/update!
                     id
                     {:name name :species species}))
           :new? false
           :respond-with-entity? true
           :delete! (fn [ctx] (animals/delete! id))
           :handle-exception handle-exception)))
  (GET "/is-dev" []
       {:status 200
        :body (pr-str is-dev?)
        :headers {"Content-Type" "application/edn"}})
  (GET "/greeting" []
       "Hello World!")
  (ANY "/"
       []
       (redirect "/index.html"))

  (resources "/" {:root "public"})
  (resources "/" {:root "/META-INF/resources"})
  (not-found "404"))

(def handler
  (-> routes
      wrap-params
      wrap-edn-params))

(defn init
  []
  (animals/init))

(defn brepl []
  (piggieback/cljs-repl
   :repl-env
   (weasel/repl-env :ip "0.0.0.0" :port 9001)))
