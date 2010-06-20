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
    (shout_close [void*] int)
    (shout_shutdown [])
    (shout_get_error [void*] constchar*)))

(def stream-formats
  {:VORBIS 0
   :MP3    1})

(def protocols
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
  ([success?] success?)
  ([success? pred form & more]
   `(do
      (when ~pred
        (let [result# ~form]
          (when-not (= ~success? result#)
            (throw (AssertionError. (str "Running " '~form " resulted in " result# " not " ~success?))))))
      (assert-success ~success? ~@more))))

(loadlib shout)

(defn open-connection
  "Open a connection to an icecast server takes a map with the following defaults:
  protocol: HTTP
  user: source
  password: - (no default)
  host: localhost
  port: 8000
  mount: - (no default)
  stream-format: VORBIS
  
  Returns a libshout native object."
  [{:keys [protocol user password host port mount stream-format]}]
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
          ms ; return
          (catch AssertionError e
            (throw (Exception. (str "Error setting up shout connection: " (.getMessage e)
                                    " caused error " (shout_get_error ms)))))))
    ; else couldn't create shoutcast connector
    (throw (Exception. "could not create shotcast connector: "))))

(defn stream
  "Stream an input-stream that supports .read to a libshout native object. This
  call is blocking until the input-stream is consumed or there is an error.  The
  input-stream data format must match the stream-format of the libshout object."
  [ms input-stream]
  (let [buffer (byte-array 1024)]
    (loop [nread (.read input-stream buffer)]
      (when (pos? nread) 
        (when-not (= (:SUCCESS errors) (shout_send ms (ByteBuffer/wrap buffer) nread))
          (throw (Exception. (str "Error sending data: " (shout_get_error ms)))))
        (shout_sync ms)
        (recur (.read input-stream buffer))))))

(defn close
  "Closes a shoutcast connection and frees the libshout native object."
  [ms]
  (when-not (= (:SUCCESS errors) (shout_close ms))
    (throw (Exception. (str "Error closing connection: " (shout_get_error ms)))))
  (shout_shutdown))
