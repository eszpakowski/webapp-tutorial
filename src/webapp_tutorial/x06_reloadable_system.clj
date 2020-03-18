(ns webapp-tutorial.x06-reloadable-system
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.http-response :refer [ok content-type not-found file-response header bad-request]]
            [hiccup.page :refer [html5]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [reitit.ring :as ring]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [integrant.core :as ig])
  (:import (java.io File)))

;Until now, every system has been launched via a defonced jetty server.
;This has a few challenges:
; * It can be a bit kludgy to stop, start, or restart the server
; * Whenever a ns is loaded, a server is launched due to the global nature of
;   the var
;
;In the ns I'll introduce integrant (https://github.com/weavejester/integrant),
; a microframework for data-driven architecture. This particular implementation
; won't do anything super exciting, but it lays the groundwork for future
; applications.
;
;To use integrant, add [integrant "0.8.0"] to you dependencies vector.
;
;Once you've done that, jump down to where we launch our server to see what's
; changed.

;Our API containing our business logic
(defn greet [greetee]
  (format "Hello, %s!" (or greetee "Clojurian")))

(defn local-files [path]
  (let [^File dir (io/file path)]
    (->> (.listFiles dir)
         (filter #(.isFile %))
         (map #(.getName %))
         (into []))))

;As a minor change to the application I'm also using Bootstrap for prettification
(defn create-download-page [path]
  (html5
    [:head
     [:link {:rel "stylesheet"
             :href "https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/css/bootstrap.min.css"
             :integrity "sha384-Vkoo8x4CGsO3+Hhxv8T/Q5PaXtkKtu6ug5TOeNV6gBiFeWPGFN9MuhOf23Q9Ifjh"
             :crossorigin "anonymous"}]]
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
     [:script {:src "https://code.jquery.com/jquery-3.4.1.slim.min.js"
               :integrity "sha384-J6qa4849blE2+poT4WnyKhv5vZF5SrPo0iEjwBvKU7imGFAV0wwj1yYfoRSJoZ+n"
               :crossorigin "anonymous"}]
     [:script {:src "https://cdn.jsdelivr.net/npm/popper.js@1.16.0/dist/umd/popper.min.js"
               :integrity "sha384-Q6E9RHvbIyZFJoft+2mJbHaEWldlvI9IOYy5n3zV9zzTtmI3UksdQRVvoxMfooAo"
               :crossorigin "anonymous"}]
     [:script {:src "https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/js/bootstrap.min.js"
               :integrity "sha384-wfSDF2E50Y2D1uUdj0O3uMBJnjuUD4Ih7YwaYd1iqfktj0Uod8GCExl3Og8ifwB6"
               :crossorigin "anonymous"}]]))

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
          (header "Content-Disposition"
                  (format "attachment; filename=\"%s\"" filename)))
      (bad-request "No filename specified."))))

(defn download-page-handler [{:keys [file-path session] :as request}]
  (ok (create-download-page file-path)))

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

;Here's the new stuff related to integrant.
; Integrant has several functions in the library, but the most important ones
; are these:
; * init-key: A multimethod that keys off of a configuration key to know how to
; turn configuration data into an initialized component
; * halt-key!: A multimethod that takes an initialized component and shuts it down
; * init: Takes a configuration map, initializes all keys, and returns the new map
;    with all keys initialized
; * halt!: Shuts down all initialized parts of a system

;Here is a very basic implementation of how to initialize a web server.
; The config contains all needed data to launch the server.
(defmethod ig/init-key :server [_ {:keys [handler] :as config}]
  (jetty/run-jetty handler config))

;This shuts the server down
(defmethod ig/halt-key! :server [_ server]
  (.stop server))

;This configuration map now has all the data needed to convert the :server key
; into a running server. At this point, we are only launching a single component
; in our system - the server. However, in future tutorials I'll show how to use
; this config map to add other stateful elements to the system such as database
; connections or even other servers.
(def config
  {:server {:host  "0.0.0.0"
            :port  3000
            :join? false
            :handler #'handler}})

;Try this out to see how to launch and stop a server
(comment
  (def s (ig/init config))
  (ig/halt! s)
  )

;Finally, here's some boilerplate that can be used to create a singleton system.

;The system is referenced by this dynamic var. Note that it isn't initialized so
;it's safe to load the ns without launching the system. You'll launch the system
;via a REPL or a main method (covered in a later lesson).
(defonce ^:dynamic *system* nil)

(defn system [] *system*)

(defn start
  ([system] (alter-var-root system (fn [s] (if-not s (ig/init config) s))))
  ([] (start #'*system*)))

(defn stop
  ([s] (alter-var-root s (fn [s] (when s (do (ig/halt! s) nil)))))
  ([] (stop #'*system*)))

(defn restart
  ([s] (do (stop s) (start s)))
  ([] (restart #'*system*)))

