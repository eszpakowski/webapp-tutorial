(ns webapp-tutorial.x05b-reitit-middleware
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.http-response :refer [ok content-type not-found file-response header bad-request]]
            [hiccup.page :refer [html5]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [reitit.ring :as ring]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]])
  (:import (java.io File)))

;In the last session we introduced the wrap-session middleware. This gave us a pretty cool web-based
;file system navigator for our application, but it had a few issues:
; 1. You have logic in several locations to compute the current path:
;    * list-files-handler
;    * show-files-handler
;    * download-file-handler
;    * download-page-handler
;   This logic has to be maintained across all the handlers
; 2. There is an obvious bug in which you can try to navigate above the root of the starting
;    file folder and this causes you to pop and empty stack and an error occurs.
;
;This tutorial is going to resolve this issue with a new middleware that injects consistent session
; path logic into our endpoints. However, we don't want this logic in every endpoint (we don't want it
; in our debug endpoints, for example). To fix this, I'll also introduce a new concept - putting the
; middleware into the router definition. This is a much cleaner way to inject your middlewares and
; makes the code easier to understand, as well.

;Our API containing our business logic
(defn greet [greetee]
  (format "Hello, %s!" (or greetee "Clojurian")))

(defn local-files [path]
  (let [^File dir (io/file path)]
    (->> (.listFiles dir)
         (filter #(.isFile %))
         (map #(.getName %))
         (into []))))

(defn create-download-page [path]
  (html5
    [:head
     [:link {:rel "stylesheet" :href "https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/css/bootstrap.min.css" :integrity "sha384-Vkoo8x4CGsO3+Hhxv8T/Q5PaXtkKtu6ug5TOeNV6gBiFeWPGFN9MuhOf23Q9Ifjh" :crossorigin "anonymous"}]]
    [:body
     [:h1 "Here's a list of files"]
     [:ul
      [:li [:span "../" [:a {:href "/index.html?path=../"} "go up"]]]
      (for [^File file (sort-by (comp cs/lower-case str) (.listFiles ^File (io/file path)))
            :let [filename (.getName file)
                  file? (.isFile file)]]
        [:li
         (if file?
           [:span
            filename
            [:a {:href (str "/api/show?filename=" filename)} " show"]
            [:a {:href (str "/api/download?filename=" filename)} " download"]]
           [:span filename [:a {:href (str "/index.html?path=" filename)} " navigate"]])])]
     [:script {:src "https://code.jquery.com/jquery-3.4.1.slim.min.js" :integrity "sha384-J6qa4849blE2+poT4WnyKhv5vZF5SrPo0iEjwBvKU7imGFAV0wwj1yYfoRSJoZ+n" :crossorigin "anonymous"}]
     [:script {:src "https://cdn.jsdelivr.net/npm/popper.js@1.16.0/dist/umd/popper.min.js" :integrity "sha384-Q6E9RHvbIyZFJoft+2mJbHaEWldlvI9IOYy5n3zV9zzTtmI3UksdQRVvoxMfooAo" :crossorigin "anonymous"}]
     [:script {:src "https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/js/bootstrap.min.js" :integrity "sha384-wfSDF2E50Y2D1uUdj0O3uMBJnjuUD4Ih7YwaYd1iqfktj0Uod8GCExl3Og8ifwB6" :crossorigin "anonymous"}]]))

;"Local" handlers
(defn hello-handler [{:keys [params] :as request}]
  (let [{:strs [name]} params]
    (ok (greet name))))

(defn request-dump-handler [request]
  (ok (with-out-str (pp/pprint request))))

(defn list-files-handler [{:keys [file-path] :as request}]
  (-> (cs/join "\n" (local-files file-path))
      ok
      (content-type "text/plain")))

(defn show-file-handler [{:keys [file-path params] :as request}]
  (let [{:strs [filename]} params
        path (str file-path "/" filename)]
    (if path
      (file-response path)
      (bad-request "No filename specified."))))

(defn download-file-handler [{:keys [file-path params] :as request}]
  (let [{:strs [filename]} params
        path (str file-path "/" filename)]
    (if path
      (-> (file-response path)
          (header "Content-Disposition" (format "attachment; filename=\"%s\"" filename)))
      (bad-request "No filename specified."))))

(defn download-page-handler [{:keys [file-path session] :as request}]
  (ok (create-download-page file-path)))

;Our new middleware that injects our path into the request. Note that it is conventional
; to name your middleware wrap-x.

(defn wrap-nav-session [handler]
  (fn [{{[root nxt :as path] :path} :session :keys [params] :as request}]
    (let [new-path-element (params "path")
          path (if root path ["."])
          path (cond
                 (nil? new-path-element) path
                 (= "../" new-path-element) (cond-> path nxt pop)
                 :default (conj path new-path-element))
          response (handler
                     (-> request
                         (assoc :file-path (cs/join "/" path))
                         (assoc-in [:session :path] path)))]
      (assoc-in response [:session :path] path))))

;I've added the middleware here
(def router
  (ring/router
    [["/debug"
      ["/hello" {:handler hello-handler}]
      ["/dump" {:handler request-dump-handler}]
      ["/params/:x" {:handler request-dump-handler}]]
     ;Add new middleware to this endpoint
     ["/index.html" {:get        download-page-handler
                     :middleware [wrap-nav-session]}]
     ;And add new middleware to these endpoints
     ["/api" {:middleware [wrap-nav-session]}
      ["/list" {:get list-files-handler}]
      ["/show" {:get show-file-handler}]
      ["/download" {:get download-file-handler}]]]
    ;Global middleware
    {:data {:middleware [wrap-params]}}
    ))

;I've removed the "thread first" middleware wrapping here.
(def handler
  (ring/ring-handler
    router
    (constantly (not-found "Sorry, I don't understand that path."))
    {:middleware [wrap-session]}))

(defonce server (jetty/run-jetty #'handler {:host  "0.0.0.0"
                                            :port  3000
                                            :join? false}))

(comment
  (require '[clojure.java.browse :refer [browse-url]])
  (browse-url "http://localhost:3000/index.html")
  (.stop server))


