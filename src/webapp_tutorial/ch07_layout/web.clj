(ns webapp-tutorial.ch07-layout.web
  "A ns containing web endpoints and routing data for the app. The primary
  output of the web ns should be the handler function. If the ns grows too large
  it may be a good idea to create a web package with the handler in a core or
  handler ns and other nses for different web apis. It is sometimes appropriate
  to put all middlewares in a middleware ns. This ns or package should concern
  itself only with handling requests, routing, invoking business logic, and
  returning a response. Never put business logic in the web ns/package."
  (:require [clojure.pprint :as pp]
            [clojure.string :as cs]
            [reitit.ring :as ring]
            [ring.util.http-response :refer [ok content-type not-found file-response header bad-request]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [webapp-tutorial.ch07-layout.api :as api]))

(defn hello-handler [{:keys [params] :as request}]
  (let [{:strs [name]} params]
    (ok (api/greet name))))

(defn request-dump-handler [request]
  (ok (with-out-str (pp/pprint request))))

(defn list-files-handler [{:keys [file-path] :as request}]
  (-> (cs/join "\n" (api/local-files file-path))
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
          (header "Content-Disposition"
                  (format "attachment; filename=\"%s\"" filename)))
      (bad-request "No filename specified."))))

(defn download-page-handler [{:keys [file-path session] :as request}]
  (ok (api/create-download-page file-path)))

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

(def handler
  (ring/ring-handler
    router
    (constantly (not-found "Sorry, I don't understand that path."))
    {:middleware [wrap-session]}))