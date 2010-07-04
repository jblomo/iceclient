(ns iceclient.transmit
  (:use [clj-native.direct :only [defclib loadlib]])
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
    (shout_set_name [void* constchar*] int)
    (shout_set_genre [void* constchar*] int)
    (shout_set_description [void* constchar*] int)
    (shout_open [void*] int)
    (shout_send [void* byte* size_t] int)
    (shout_sync [void*])
    (shout_metadata_new [] void*)
    (shout_metadata_add [void* constchar* constchar*] int)
    (shout_set_metadata [void* void*] int)
    (shout_metadata_free [void*])
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

(loadlib shout)


(defmacro assert-success
  ([success?] success?)
  ([success? pred form & more]
   `(do
      (when ~pred
        (let [result# ~form]
          (when-not (= ~success? result#)
            (throw (AssertionError. (str "Running " '~form " resulted in " result# " not " ~success?))))))
      (assert-success ~success? ~@more))))


(defn open
  "Open a connection to an icecast server takes a map with the following defaults:
  protocol: (:HTTP protocols)
  user: source
  password: - (no default)
  host: localhost
  port: 8000
  mount: - (no default)
  stream-format: (:VORBIS stream-formats)
  ;; keys for client display:
  display-name: 'no name'
  genre: -
  description: -
  
  Returns a libshout native object."
  [{:keys [protocol user password host port mount stream-format display-name genre description]}]
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
               display-name (shout_set_name ms display-name)
               genre (shout_set_genre ms genre)
               description (shout_set_description ms description)
               true (shout_open ms))
          ms ; return
          (catch AssertionError e
            (throw (Exception. (str "Error setting up shout connection: " (.getMessage e)
                                    " caused error " (shout_get_error ms)))))))
    ; else couldn't create shoutcast connector
    (throw (Exception. "could not create shotcast connector: "))))


(defn- update-metadata
  "Typically used as a watch callback. Takes a map of metadata, creates a
  libshout native metadata object which is used to set the stream metadata
  information."
  [ms _ _ metadata]
  (if-let [shoutm (shout_metadata_new)]
    (do
      (doseq [[k v] metadata]
        (let [m-name  (if (keyword? k) (name k) (str k))
              m-value (if (keyword? v) (name v) (str v))
              result (shout_metadata_add shoutm m-name m-value)]
          (when-not (= (:SUCCESS errors) result)
            (shout_metadata_free shoutm)
            (throw (Exception. (str "Failed to setup metadata " m-name " " m-value " : " result))))))
      (when-not (= (:SUCCESS errors) (shout_set_metadata ms shoutm))
        (throw (Exception. (str "Failed to set stream metadata: " (shout_get_error ms) " with map " metadata))))
      (shout_metadata_free shoutm))
    ; else no shoutm
    (throw (Exception. "Failed to create libshout metadata native object"))))


(defn- setup-stream-metadata
  "Takes a metadata map or reference to a map.  If a references, sets up watch
  for changes.  Returns inital map to set the metadata."
  [metadata ms]
  (if (map? metadata)
    metadata
    (do
      (add-watch metadata ms update-metadata)
      @metadata)))


(defn stream
  "Stream an input-stream that supports .read to a libshout native object. This
  call is blocking until the input-stream is consumed, continue? returns false,
  or there is an error.  The input-stream data format must match the
  stream-format of the libshout object.

  Optionally, a metadata map or reference to a map may be passed in.  A
  reference will be watched for changes and the stream updated accordingly.

  Optionally, after the metadata map, you may specify a 'continues' function.
  If the function returns false, the stream will complete early."
  ([ms input-stream]
   (stream ms input-stream nil (constantly true)))

  ([ms input-stream metadata]
   (stream ms input-stream metadata (constantly true)))

  ([ms input-stream metadata continue?]
   (when metadata
     (let [metadata-init (setup-stream-metadata metadata ms)]
       (update-metadata ms nil nil metadata-init)))

   (let [buffer (byte-array 1024)]
     (loop [nread (.read input-stream buffer)]
       (when (and (pos? nread) (continue?))
         (when-not (= (:SUCCESS errors) (shout_send ms (ByteBuffer/wrap buffer) nread))
           (throw (Exception. (str "Error sending data: " (shout_get_error ms)))))
         (shout_sync ms)
         (recur (.read input-stream buffer)))))))


(defn close
  "Closes a shoutcast connection and frees the libshout native object."
  [ms]
  (when-not (= (:SUCCESS errors) (shout_close ms))
    (throw (Exception. (str "Error closing connection: " (shout_get_error ms)))))
  (shout_shutdown))


(defn test-shout
  "Setup and example stream.  Requires icecast running and file available"
  []
  (let [mymeta {:song "songtest"}
        mystream (open {:password "hackme"
                        :mount "testshout.mp3"
                        :stream-format (:MP3 stream-formats)
                        :display-name "test-shout"
                        :description "test-shout stream with example file"})]
    (with-open [is (FileInputStream. "/Users/jim/music_test/ONHE.mp3")]
      (stream mystream is mymeta))))
