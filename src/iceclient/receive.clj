(ns iceclient.receive
  (:use [clojure-http.client :only [request url-encode]])
  (:import [javazoom.jl.player Player]
           [java.io File BufferedInputStream FileNotFoundException]))


(defn play-url
  "Uses the default Java sound output to play the mp3 stream given.  Blocks
  until stream is complete."
  [url]
  (.play (Player. (-> (:connection (request url))
                    (.getInputStream) 
                    (BufferedInputStream.)))))
