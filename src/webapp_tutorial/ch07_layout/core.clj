(ns webapp-tutorial.ch07-layout.core
  "A ns containing a main method for launching your system. This is the only ns
  that should be aware of your system since it will be launching it as its
   primary role."
  (:gen-class)
  (:require [webapp-tutorial.ch07-layout.system :as sys]))

(defn -main [& [port]]
  (let [system (sys/start)]
    (println "System started!")
    (try
      (.addShutdownHook
        (Runtime/getRuntime)
        (let [^Runnable shutdown #(sys/stop)]
          (Thread. shutdown)))
      (catch Throwable t
        (sys/stop)))))