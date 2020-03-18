(ns webapp-tutorial.ch07-layout.system
  "A ns containing system configuration. It should primarily consist of:
   * integrant multimethods for components - Note that it is also a good idea to
   build up a set of common multimethods in a different package or project for
   reuse
   * The configuration map.
   * The system boilerplate to start, stop, restart, etc.
   Note that you don't have to use a dynamic var for your system. An atom is
   also fine, but using a dynamic var works pretty well.

   One thing that I'll discuss later is that it is critical that the web and api
   nses NEVER know about this ns. It is a sign of complex architecture and you
   will almost certainly have circular references in your project. I will
   discuss how to inject the needed data into your application such that the
   transport layers (e.g. web) and api layers need never know about the system."
  (:require [integrant.core :as ig]
            [webapp-tutorial.ch07-layout.web :as web]
            [ring.adapter.jetty :as jetty]))

(defmethod ig/init-key :server [_ {:keys [handler] :as config}]
  (jetty/run-jetty handler config))

(defmethod ig/halt-key! :server [_ server]
  (.stop server))

(def config
  {:server {:host  "0.0.0.0"
            :port  3000
            :join? false
            :handler #'web/handler}})

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