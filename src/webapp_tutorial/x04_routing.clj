(ns webapp_tutorial.x04-routing
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.http-response :refer [ok content-type not-found file-response header bad-request]]
            [hiccup.page :refer [html5]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [reitit.ring :as ring])
  (:import (java.io File)))

;The next stage of our app is to introduce a routing library. Up until now,
; we've done routing with a simple case statement. This is ok for very simple
; applications, but even then you really should use a routing library. Two
; popular routing libraries are Compojure and Reitit. Reitit is a newer and more
; data-driven API that I'll be using here. Add the dependency to your project as
; [metosin/reitit "0.4.2"].

;Our API containing our business logic
(defn greet [greetee]
  (format "Hello, %s!" (or greetee "Clojurian")))

(defn local-files []
  (let [^File dir (io/file ".")]
    (->> (.listFiles dir)
         (filter #(.isFile %))
         (map #(.getName %))
         (into []))))

(defn create-download-page []
  (html5
    [:h1 "Here's a list of files"]
    [:ul
     (for [file (local-files)]
       [:li
        [:p
         file
         [:a {:href (str "/api/show?filename=" file)} " show"]
         [:a {:href (str "/api/download?filename=" file)} " download"]]])]))

;"Local" handlers
(defn hello-handler [{:keys [query-string] :as request}]
  (let [[_ greetee] (some->> query-string (re-matches #"name=(.+)"))]
    (ok (greet greetee))))

(defn request-dump-handler [request]
  (ok (with-out-str (pp/pprint request))))

(defn list-files-handler [request]
  (-> (ok (cs/join "\n" (local-files)))
      (content-type "text/plain")))

(defn show-file-handler [{:keys [query-string] :as request}]
  (let [[_ filename] (some->> query-string (re-matches #"filename=(.+)"))]
    (if filename
      (file-response filename)
      (bad-request "No filename specified."))))

(defn download-file-handler [{:keys [query-string] :as request}]
  (let [[_ filename] (some->> query-string (re-matches #"filename=(.+)"))]
    (if filename
      (-> (file-response filename)
          (header "Content-Disposition" (format "attachment; filename=\"%s\"" filename)))
      (bad-request "No filename specified."))))

(defn download-page-handler [request]
  (ok (create-download-page)))

;Notice that we've now separated our global handler into router, which is just a
; vector of routes and maps describing the endpoints associated with each route,
; and a handler, which is created by passing the router to the ring-handler
; function. For now, we're just mapping the desired HTTP method to the
; individual web handlers. We'll add more information to the router data later.

;This design provides a very clean architectural separation between the server,
; the router, the global handler, the individual handler, and the business logic
; layer. Starting from the top, any of these items can be tested, debugged, and
; developed in isolation. It is trivial to change one of these components
; independent of the rest.

;Notice also that I've nested the hello and debug endpoints into a debug path
; and the list, show, and download endpoints into an api path. This is one nice
; thing about using a routing library. You set up the routes and let the library
; deal with the matching.
(def router
  (ring/router
    [["/debug"
      ["/hello" {:handler hello-handler}]
      ["/dump" {:handler request-dump-handler}]]
     ["/index.html" {:get download-page-handler}]
     ["/api"
      ["/list" {:get list-files-handler}]
      ["/show" {:get show-file-handler}]
      ["/download" {:get download-file-handler}]]]))

(def handler
  (ring/ring-handler
    router
    (constantly (not-found "Sorry, I don't understand that path."))))

(defonce server (jetty/run-jetty #'handler {:host  "0.0.0.0"
                                            :port  3000
                                            :join? false}))

(comment
  (require '[clojure.java.browse :refer [browse-url]])
  (browse-url "http://localhost:3000")
  (.stop server))

