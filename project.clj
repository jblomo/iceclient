(defproject org.clojars.jim/iceclient "0.0.3-SNAPSHOT"
            :description "Icecast source and client to transmit and recieve streaming audio."
            :dependencies [[org.clojure/clojure "1.2.0-master-SNAPSHOT"]
                           [clojure-http-client "1.1.0-SNAPSHOT"]
                           [net.javazoom/jlayer "1.0.1"]
                           [clj-native "0.9.1-SNAPSHOT"]]
            :jvm-opts ["-Djna.library.path=/opt/local/lib"])
