(ns webapp-tutorial.x05a-session-middleware
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

;In the last session I introduced the concept of middleware. To further
; illustrate how middlewares can ve used to extend our app I will introduce the
; wrap-session middleware. This will create a stateful web session that we can
; use to add browsing to our file server api.

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
    [:h1 "Here's a list of files"]
    [:ul
     [:p "../" [:a {:href "/index.html?path=../"} "go up"]]
     (for [^File file (.listFiles ^File (io/file path))
           :let [filename (.getName file)
                 file? (.isFile file)]]
       [:li
        (if file?
          [:p
           filename
           [:a {:href (str "/api/show?filename=" filename)} " show"]
           [:a {:href (str "/api/download?filename=" filename)} " download"]]
          [:p filename [:a {:href (str "/index.html?path=" filename)} " navigate"]])])]))

;"Local" handlers
(defn hello-handler [{:keys [params] :as request}]
  (let [{:strs [name]} params]
    (ok (greet name))))

(defn request-dump-handler [request]
  (ok (with-out-str (pp/pprint request))))

(defn list-files-handler [{:keys [session] :as request}]
  (let [path (get-in session [:path] ".")]
    (-> (ok (cs/join "\n" (local-files path)))
        (content-type "text/plain"))))

(defn show-file-handler [{:keys [session params] :as request}]
  (let [{:strs [filename]} params
        filename (cs/join "/" (-> session :path (conj filename)))]
    (if filename
      (file-response filename)
      (bad-request "No filename specified."))))

(defn download-file-handler [{:keys [session params] :as request}]
  (let [{:strs [filename]} params
        path (cs/join "/" (-> session :path (conj filename)))]
    (if path
      (-> (file-response path)
          (header "Content-Disposition"
                  (format "attachment; filename=\"%s\"" filename)))
      (bad-request "No filename specified."))))

(defn download-page-handler [{:keys [session params] :as request}]
  (let [new-path-element (or (params "path") ".")
        pathvec (vec (:path session))
        path (if (= new-path-element "../")
               (pop pathvec)
               (cond-> pathvec new-path-element (conj new-path-element)))]
    (-> (ok (create-download-page (cs/join "/" path)))
        (assoc-in [:session :path] path))))

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
  (-> (ring/ring-handler
        router
        (constantly (not-found "Sorry, I don't understand that path.")))
      wrap-session
      wrap-params))

(defonce server (jetty/run-jetty #'handler {:host  "0.0.0.0"
                                            :port  3000
                                            :join? false}))

(comment
  (require '[clojure.java.browse :refer [browse-url]])
  (browse-url "http://localhost:3000/index.html")
  (.stop server))

