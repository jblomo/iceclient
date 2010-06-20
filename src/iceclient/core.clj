(ns iceclient.core
  (:use [clj-native.direct :only [defclib loadlib typeof]]
        [clj-native.structs :only [byref byval]]
        [clj-native.callbacks :only [callback]])
  (:import
    [java.io File BufferedInputStream FileInputStream FileNotFoundException]
    [java.nio ByteBuffer]))

(System/setProperty "jna.library.path" "/opt/local/lib")

(defclib
  shout
  (:libname "shout")
  (:functions
    (shout_version [int* int* int*] constchar*)
    (shout_init [])
    (shout_new [] void*)
    (shout_set_host [void* constchar*] int)
    (shout_set_protocol [void* int] int)
    (shout_set_port [void* short] int)
    (shout_set_password [void* constchar*] int)
    (shout_set_mount [void* constchar*] int)
    (shout_set_user [void* constchar*] int)
    (shout_set_format [void* int] int)
    (shout_open [void*] int)
    (shout_send [void* byte* size_t] int)
    (shout_sync [void*])
    (shout_close [void*])
    (shout_shutdown [])
    (shout_get_error [void*] constchar*)))

(def stream-formats
  {:VORBIS 0
   :MP3    1})

(def protocol
  {:HTTP 0
   :XAUDIOCAST 1
   :ICY 2})

(def errors
  {:SUCCESS     0
   :INSANE	-1
   :NOCONNECT	-2
   :NOLOGIN	-3
   :SOCKET	-4
   :MALLOC	-5
   :METADATA	-6
   :CONNECTED	-7
   :UNCONNECTED	-8
   :UNSUPPORTED	-9
   :BUSY	-10})

(def error-codes (zipmap (vals errors) (keys errors)))

(defmacro assert-success
  [success? & forms]
  `(doseq [[pred# form#] (partition 2 '~forms)]
     (when pred#
       (let [result# (eval form#)]
         (when-not (= ~success? result#)
           (throw (AssertionError. [result# (str "Running " form# " resulted in " result# " not " ~success?)])))))))
       
(loadlib shout)

(defn stream
  "Send an MP3 stream to a given Icecast instance"
  [inputstream {:keys [protocol user password host port mount stream-format]}]
  (shout_init)
  (if-let [ms (shout_new)]
    (do (try (assert-success
               (:SUCCESS errors)
               protocol (shout_set_protocol ms protocol)
               user (shout_set_user ms user)
               password (shout_set_password ms password)
               host (shout_set_host ms host)
               port (shout_set_port ms port)
               mount (shout_set_mount ms mount)
               stream-format (shout_set_format ms stream-format)
               true (shout_open ms))
          (catch AssertionError e (throw (Exception. (str "Error setting up shout connection: " (.getMessage e) " caused error " (shout_get_error ms))))))
      (
    ; else couldn't create shoutcast connector
    (throw (Exception. (str "could not create shotcast connector: " (shout_get_error ms))))



(shout_version nil nil nil)

(shout_init)

(def ms (shout_new))

(defn stream
  "stream an mp3 file to icecast"
  [file]
  (with-open [f (FileInputStream. file)]
    (let [buffer (byte-array 1024)]
      (loop [nread (.read f buffer)]
        (if (pos? nread) 
          (do 
            (shout_send ms (ByteBuffer/wrap buffer) nread)
            (shout_sync ms)
            (recur (.read f buffer)))
          (println "end stream"))))))

;; (shout_close ms
;; (shout_shutdown ms
