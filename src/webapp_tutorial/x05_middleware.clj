(ns webapp-tutorial.x05-middleware
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.http-response :refer [ok content-type not-found file-response header bad-request]]
            [hiccup.page :refer [html5]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [reitit.ring :as ring]
            [ring.middleware.params :refer [wrap-params]])
  (:import (java.io File)))

;I'll now introduce one of the most powerful concepts of building webapps in Clojure:
; Middleware.
; A middleware is a function that takes your original handler and returns a new
; handler. In this way, you can modify or extend your handler with all kinds of
; useful behaviors. For example, here is a middleware that adds timing information
; to your response:

(defn wrap-add-timing [handler]
  (fn [request]
    (let [ti (System/nanoTime)
          response (handler request)
          dt (* 1.0E-6 (- (System/nanoTime) ti))]
      (assoc response :dt-sec dt))))

;To use this middleware you would "chain" it like so:
(comment
  (-> handler
      wrap-add-timing
      wrap-other-middlewares
      ;and so on
      )
  )
;To this point we have used no middlewares. You don't generally write much middleware
;yourself because there exists a lot of standard middlewares out there, especially in
;the ring ecosystem, a set of Clojure libraries developed by James Reeves (weavejester).

;If you jump down to our handler I'll introduce some common middlewares.

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
(defn hello-handler [{:keys [params] :as request}]
  (let [{:strs [name]} params]
    (ok (greet name))))

(defn request-dump-handler [request]
  (ok (with-out-str (pp/pprint request))))

(defn list-files-handler [request]
  (-> (ok (cs/join "\n" (local-files)))
      (content-type "text/plain")))

(defn show-file-handler [{:keys [params] :as request}]
  (let [{:strs [filename]} params]
    (if filename
      (file-response filename)
      (bad-request "No filename specified."))))

(defn download-file-handler [{:keys [params] :as request}]
  (let [{:strs [filename]} params]
    (if filename
      (-> (file-response filename)
          (header "Content-Disposition" (format "attachment; filename=\"%s\"" filename)))
      (bad-request "No filename specified."))))

(defn download-page-handler [request]
  (ok (create-download-page)))

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

;I've added the wrap-params middleware. The easiest way to add the ring middlewares
;is just to include the ring project in your dependencies ([ring "1.8.0"]).

;If you now navigate to http://localhost:3000/debug/dump?name=Mark you can inspect the
;result and see that the request has been enhanced with both a :params and a
; :query-params key. I now no longer need to regular expression matching on my
; :query-string. I've updated the handlers above to reflect these new keys.
; Also, be aware that the params are string keys, so I am destructuring them with :strs
; vs. :keys. You could write a middleware to keywordize params if you wanted.

;The are a huge number of middlewares available and many people just import and use the
; wrap defaults (https://github.com/ring-clojure/ring-defaults) middlewares as sane
; defaults for their web applications. You just wrap your basic handler with one of
; the four default sets of middleware described here https://github.com/ring-clojure/ring-defaults#basic-usage.
(def handler
  (-> (ring/ring-handler
        router
        (constantly (not-found "Sorry, I don't understand that path.")))
      wrap-params))

(defonce server (jetty/run-jetty #'handler {:host  "0.0.0.0"
                                            :port  3000
                                            :join? false}))

(comment
  (require '[clojure.java.browse :refer [browse-url]])
  (browse-url "http://localhost:3000/debug/dump?name=Mark")
  (.stop server))

