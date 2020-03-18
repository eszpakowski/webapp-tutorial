(defproject webapp-tutorial "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [ring/ring-jetty-adapter "1.8.0"]
                 [metosin/ring-http-response "0.9.1"]
                 [hiccup "1.0.5"]
                 [metosin/reitit "0.4.2"]
                 [ring "1.8.0"]
                 [integrant "0.8.0"]
                 [ring/ring-mock "0.4.0"]]

  :main webapp-tutorial.ch07-layout.core
  )
