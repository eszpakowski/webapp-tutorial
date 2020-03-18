(ns webapp-tutorial.x05c-middleware-order
  (:require [reitit.ring :as ring]
            [ring.util.http-response :refer [ok unauthorized]]))

;This section of the tutorial is an aside to help understand one of the most
; confusing aspects of the middleware pattern - when the request is modified and
; when the handler is called. The middleware pattern creates a stack of
; operations in which the request is built up as the stack is pushed and the
; handlers are called as the stack is popped.
;
;At any point, a middleware can short circuit and not call the inbound handler.
; This is very useful for things like authentication or other stages in which a
; particular criteria needs to be met for success.

(defn wrap-order [handler i]
  (fn [request]
    (println (format "pre-handler[%s]\trequest=%s" i request))
    (let [response (-> (handler (update request :pre (comp vec conj) i))
                       (update :pst (comp vec conj) i)
                       (assoc :pre (request :pre)))]
      (println (format "pst-handler[%s]\trespose=%s" i response))
      response)))

(defn wrap-auth [handler]
  (fn [{:keys [username password] :as request}]
    (if (and username password)
      (handler request)
      (unauthorized "You need to provide a username and password."))))

(defn wrap-creds [handler username password]
  (fn [request]
    (handler (assoc request :username username :password password))))

(defn simple-handler [request]
  (println request)
  (ok "OK"))

;Middlewares with reitit routing are applied from the outside of the router in,
; such that the first middleware in the global mw modifies the request first,
; then the remaining mws in that sequence are applied. The next set of inner mws
; are applied, finally with the last, innermost middleware modifying the request
; last. Handlers are applied in reverse order with the innermost being called
; first and the outermost called last.
(def router
  (ring/router
    [;And add new middleware to these endpoints
     ["/api" {:middleware [[wrap-order 2]
                           [wrap-order 3]]}
      ["/list" {:get        simple-handler
                :middleware [[wrap-order 4]
                             [wrap-order 5]]}]]
     ["/secure_api" {:middleware [wrap-auth
                                  [wrap-order 2]
                                  [wrap-order 3]]}
      ["/list" {:get        simple-handler
                :middleware [[wrap-order 4]
                             [wrap-order 5]]}]]]
    ;Global middleware
    {:data {:middleware [[wrap-order 0]
                         [wrap-order 1]]}}))

;I've removed the "thread first" middleware wrapping here.
(def reitit-handler (ring/ring-handler router))

;When threaded, the middleware modifies the request in reverse order since each
; successive item wraps the previous. In other words, the last mw applied is the
; first to modify the request and the last handler to be called.
(def wrapped-simple-handler
  (-> simple-handler
      (wrap-order 0)
      (wrap-order 1)
      (wrap-order 2)))

(comment
  ((wrap-order wrapped-simple-handler 3) {})

  ((wrap-order reitit-handler 6) {:protocol       "HTTP/1.1",
                                  :server-port    80,
                                  :server-name    "localhost",
                                  :remote-addr    "127.0.0.1",
                                  :uri            "/api/list",
                                  :scheme         :http,
                                  :request-method :get,
                                  :headers        {"host" "localhost"}})

  (reitit-handler
    {:protocol       "HTTP/1.1",
     :server-port    80,
     :server-name    "localhost",
     :remote-addr    "127.0.0.1",
     :uri            "/secure_api/list",
     :scheme         :http,
     :request-method :get,
     :headers        {"host" "localhost"}})

  ((wrap-creds reitit-handler "mbastian" "password!123")
   {:protocol       "HTTP/1.1",
    :server-port    80,
    :server-name    "localhost",
    :remote-addr    "127.0.0.1",
    :uri            "/secure_api/list",
    :scheme         :http,
    :request-method :get,
    :headers        {"host" "localhost"}})

  )

