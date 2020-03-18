(ns webapp_tutorial.x03b-server-side-rendering
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.http-response :refer [ok content-type not-found file-response header bad-request]]
            [hiccup.page :refer [html5]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as cs])
  (:import (java.io File)))

;Our next logical stage in this tutorial series is routing using a routing library,
; but the ugliness of the hand-crafted HTML compels me to introduce hiccup instead.
; Hiccup is a wonderful library (add [hiccup "1.0.5"] to your dependencies) that
; converts Clojure data strucutures to HTML. This makes it trivial to
; programmatically generate HTML on the server.

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
         [:a {:href (str "/show?filename=" file)} " show"]
         [:a {:href (str "/download?filename=" file)} " download"]]])]))

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

;"Global" handler which is mostly routing to local handlers
(defn handler [{:keys [uri] :as request}]
  (case uri
    "/hello" (hello-handler request)
    "/dump" (request-dump-handler request)
    "/list" (list-files-handler request)
    "/index.html" (download-page-handler request)
    "/show" (show-file-handler request)
    "/download" (download-file-handler request)
    (not-found "Sorry, I don't understand that path.")))

(defonce server (jetty/run-jetty #'handler {:host  "0.0.0.0"
                                            :port  3000
                                            :join? false}))

(comment
  (require '[clojure.java.browse :refer [browse-url]])
  (browse-url "http://localhost:3000")
  (.stop server))
