(ns zodiac.ext.sql
  (:refer-clojure :exclude [count])
  (:require [clojure.tools.logging :as log]
            [honey.sql]
            [integrant.core :as ig]
            [lambdaisland.uri :as uri]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as jdbc.connection]
            [next.jdbc.date-time])
  (:import [com.zaxxer.hikari HikariDataSource]))

;; read all db date times as java.time.Instant
(next.jdbc.date-time/read-as-instant)

(create-ns 'zodiac.core)
(alias 'z 'zodiac.core)

(defmethod ig/init-key ::pool [_ spec]
  (jdbc.connection/->pool HikariDataSource spec))

(defmethod ig/halt-key! ::pool [_ pool]
  (log/debug "Closing db...\n")
  (.close pool))

(defmethod ig/init-key ::db [_ {:keys [connectable jdbc-options]}]
  (jdbc/with-options connectable (or jdbc-options
                                     jdbc/snake-kebab-opts)))

(defn execute!
  "Execute a SQL statement. The stmt can either be a SQL vector or a HoneySQL map
  statement.

  Returns a sequence of maps.
  "
  ([db stmt]
   (execute! db stmt {}))
  ([db stmt opts]
   (let [stmt (if (map? stmt)
                (honey.sql/format stmt)
                stmt)]
     (jdbc/execute! db stmt opts))))

(defn execute-one!
  "Execute a SQL statement. The stmt can either be a SQL vector or a HoneySQL map
  statement.

  Returns a single result as a map.
  "
  ([db stmt]
   (execute-one! db stmt {}))
  ([db stmt opts]
   (let [stmt (if (map? stmt)
                (honey.sql/format stmt)
                stmt)]
     (jdbc/execute-one! db stmt opts))))

(defn exists?
  "Wrap a HoneySQL statement in a `select exists(...)`

  Returns true or false.
  "
  ([db stmt]
   (exists? db stmt {}))
  ([db stmt opts]
   {:pre [(map? stmt)]}
   (let [r (execute-one! db {:select [[[:exists stmt] :exists?]]} opts)]
     (or (= (:exists? r) true)
         (= (:exists? r) 1)))))

(defn count
  "Wrap a HoneySQL statement in a `select count(...)`

  Returns true or false. "
  ([db stmt]
   (count db stmt {}))
  ([db stmt opts]
   {:pre [(map? stmt)]}
   (-> (execute-one! db
                     {:select [[[:count :*] :count]]
                      :from [[stmt :c]]}
                     opts)
       :count)))

(defn database-url->jdbcUrl
  "Parse a database url into a jdbc url.


  This function isn't exhaustive and probably won't work for more exotic JDBC
  URLs.
  "
  [db-url]
  (let [{:keys [scheme user password host port path query]} (uri/uri db-url)
        _ (tap> (str "query: " query))
        query (cond-> (uri/query-string->map query)
                (seq user) (assoc :user user)
                (seq password) (assoc :password password)
                :always (uri/map->query-string))]
    (cond-> (str "jdbc:" scheme ":" )
      (#{"mysql" "postgresql" "postgres" "sqlserver"} scheme) (str "//")
      (seq host) (str host)
      (seq port) (str ": " port)
      (seq path) (str path)
      (seq query) (str "?" query))))

(defn init [{:keys [spec context-key jdbc-options]
             :or {context-key ::db}
             :as _options}]
  (fn [config]
    (-> config
        (assoc ::pool spec
               ::db {:connectable (ig/ref ::pool)
                     :jdbc-options jdbc-options})
        ;; Add the key to the request context
        (assoc-in [::z/middleware :context context-key] (ig/ref ::db)))))
