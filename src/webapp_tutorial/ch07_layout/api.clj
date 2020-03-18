(ns webapp-tutorial.ch07-layout.api
  "A ns containing business/API logic for the application.
  Notes regarding this ns:
   * There is no need for all logic to reside in this ns. It it grows too large,
   create an api package and put your logic nses in there or bring in your logic
   from another lib.
   * It is critical to never put any web logic here. That is a transport
   mechanism and your api should know nothing about the transport layer."
  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [hiccup.page :refer [html5]])
  (:import (java.io File)))

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
