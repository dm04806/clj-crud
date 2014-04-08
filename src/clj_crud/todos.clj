(ns clj-crud.todos
  (:require [clojure.tools.logging :refer [debug spy]]
            [clojure.edn :as edn]
            [clj-crud.util.helpers :as h]
            [clj-crud.util.layout :as l]
            [clj-crud.common :as c]
            [clj-crud.data.accounts :as accounts]
            [clj-crud.data.todos :as todos]
            [liberator.core :refer [resource defresource]]
            [compojure.core :refer [defroutes ANY GET]]
            [net.cgrand.enlive-html :as html]
            [cemerick.friend :as friend]))

(def todos-page-html (html/html-resource "templates/todos.html"))

(defn todos-page-layout [ctx]
  (c/emit-application
   ctx
   [:head html/last-child] (html/after (html/select todos-page-html [:head html/first-child]))
   [:#content] (html/substitute (html/select todos-page-html [:#content]))
   [:#csrf-token] (html/set-attr :value (get-in ctx [:request :session "__anti-forgery-token"]))))

(defresource todos-page
  :allowed-methods [:get :post]
  :available-media-types ["text/html"]
  :authorized? (fn [ctx]
                 (friend/identity (get ctx :request)))
  :handle-unauthorized (fn [ctx]
                         (h/location-flash "/login"
                                           "Please login"))
  :allowed? (fn [ctx]
                 (let [slug (get-in ctx [:request :params :slug])]
                   (friend/authorized? [(keyword slug)] (friend/identity (get ctx :request)))))
  :handle-forbidden (fn [ctx]
                      (h/location-flash "/login"
                                        "Not allowed"))
  :exists? (fn [ctx]
             (let [slug (get-in ctx [:request :params :slug])]
               (when-let [account (accounts/get-account (h/db ctx) slug)]
                 {:account account})))
  :handle-ok (fn [ctx]
               {:account (:account ctx)})
  :as-response (l/as-template-response todos-page-layout))

(defresource todos
  :allowed-methods [:get :post]
  :available-media-types ["application/edn"]
  :authorized? (fn [ctx]
                 (friend/identity (get ctx :request)))
  :allowed? (fn [ctx]
                 (let [slug (get-in ctx [:request :params :slug])]
                   (friend/authorized? [(keyword slug)] (friend/identity (get ctx :request)))))
  ;; :exists? (fn [ctx]
  ;;            (let [slug (get-in ctx [:request :params :slug])]
  ;;              (when-let [account (accounts/get-account (h/db ctx) slug)]
  ;;                {:account account})))
  :post! (fn [ctx]
           (let [text (-> (get-in ctx [:request :body])
                          slurp
                          edn/read-string
                          :text)
                 id (todos/create-todo (h/db ctx)
                                       (friend/current-authentication (get ctx :request))
                                       {:text text})]
             {:id id}))
  :new (fn [ctx]
         (:id ctx))
  :handle-created (fn [ctx]
                    {:id (:id ctx)})
  :handle-ok (fn [ctx]
               {:todos (todos/get-todos (h/db ctx)
                                        (friend/current-authentication (get ctx :request)))})
  :as-response (l/as-template-response nil))

(defroutes todos-routes
  (ANY "/todos/:slug" _ todos-page)
  (ANY "/todos/:slug/todos" _ todos))
