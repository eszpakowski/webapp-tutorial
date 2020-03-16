(ns webapp_tutorial.x03a-advanced-responses
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.http-response :refer [ok content-type not-found file-response header bad-request]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as cs])
  (:import (java.io File)))

;We'll now modify our app to create a file server.
;Note that you will want to add some security and whatnot in practice.
;All we're doing to make this happen is extending our BI layer a little and
; wrapping handlers around that API using the response library.

;Our API containing our business logic
(defn greet [greetee]
  (format "Hello, %s!" (or greetee "Clojurian")))

(defn local-files []
  (let [^File dir (io/file ".")]
    (->> (.listFiles dir)
         (filter #(.isFile %))
         (map #(.getName %))
         (into []))))

(defn create-li [filename]
  (format
    "<li>
    <p>%s
     <a href=\"/show?filename=%s\">show</a>
     <a href=\"/download?filename=%s\">download</a>
     </p></li>"
    filename filename filename))

(defn create-download-page []
  (format
    "<!DOCTYPE html>
       <html>
       <h1>Here's a list of files</h1>
      <ul>
        %s
      </ul>
    </html>"
    (cs/join "\n" (for [file (local-files)] (create-li file)))))

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
