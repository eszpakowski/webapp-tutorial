(ns webapp_tutorial.x02-routes
  (:require [ring.adapter.jetty :as jetty]
            [clojure.pprint :as pp]))

;Handlers have 3 main concerns:
;* Routing - what subhandler/business logic is to be executed
;* Business Logic - The actual logic you want to execute
;* Response - Transform the BL result into an appropriate HTTP response

;In this namespace we split out our API, handler, and routing logic.

;Our API containing our business logic
(defn greet [greetee]
  (format "Hello, %s!" (or greetee "Clojurian")))

;"Local" handlers. These call out to APIs (very simple functions in this case)
; and then return a response. Handlers should only unpack a request, call out
; to your API, and pack that result into a response.
(defn hello-handler [{:keys [query-string] :as request}]
  (let [[_ greetee] (some->> query-string (re-matches #"name=(.+)"))]
    {:status 200
     :body   (greet greetee)}))

(defn request-dump-handler [request]
  {:status 200
   :body   (with-out-str (pp/pprint request))})

;"Global" handler which is mostly routing to local handlers.
;Here we have some very simple routing logic based on matching the uri from the request.
(defn handler [{:keys [uri] :as request}]
  (case uri
    "/hello" (hello-handler request)
    "/dump" (request-dump-handler request)
    {:status 404
     :body   "Sorry, I only understand hello and dump"}))

(defonce server (jetty/run-jetty #'handler {:host  "0.0.0.0"
                                            :port  3000
                                            :join? false}))

(comment
  (require '[clojure.java.browse :refer [browse-url]])
  (browse-url "http://localhost:3000")
  (.stop server)

  (handler
    {:protocol       "HTTP/1.1",
     :server-port    80,
     :server-name    "localhost",
     :remote-addr    "127.0.0.1",
     :uri            "/hello",
     :query-string   "name=Mark",
     :scheme         :http,
     :request-method :get,
     :headers        {"host" "localhost"}})

  )