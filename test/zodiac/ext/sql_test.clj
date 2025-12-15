(ns zodiac.ext.sql-test
  (:refer-clojure :exclude [count])
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [integrant.core :as ig]
            [lambdaisland.uri :as uri]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.result-set :as jdbc.result-set]
            [zodiac.ext.sql :as z.sql]))

(add-tap println)

(def ^:dynamic *system*)
(def ^:dynamic *db*)

(use-fixtures :each (fn [f]
                      (let [config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:"}
                                    ::z.sql/db {:connectable (ig/ref ::z.sql/pool)}}]
                        (alter-var-root #'*system* (constantly (ig/init config)))
                        (alter-var-root #'*db* (constantly (::z.sql/db *system*)))
                        (f)
                        (ig/halt! *system*))))

(deftest database-url->jdbcUrl
  (testing "parses database url"
    (let [jdbc-url (z.sql/database-url->jdbcUrl "postgresql://user:password@localhost:1234/somedb?opt=true")
          parsed (-> jdbc-url
                     (subs 5)
                     (uri/uri))]
      (is (str/starts-with? jdbc-url "jdbc:"))
      (is (match? {:scheme "postgresql"
                   :host "localhost"
                   :port "1234"
                   :path "/somedb"
                   :user nil
                   :password nil}
                  (select-keys parsed [:scheme :host :port :path :user :password])))
      (is (match? {:opt "true"
                   :user "user"
                   :password "password"}
                  (uri/query-map jdbc-url)))))

  (testing "parses database url"
    (let [jdbc-url (z.sql/database-url->jdbcUrl "postgresql://localhost:1234/somedb")
          parsed (-> jdbc-url
                     (subs 5)
                     (uri/uri))]
      (is (str/starts-with? jdbc-url "jdbc:"))
      (is (match? {:scheme "postgresql"
                   :host "localhost"
                   :port "1234"
                   :path "/somedb"
                   :query nil
                   :user nil
                   :password nil}
                  (select-keys parsed [:scheme :host :port :path :query :user :password])))))

  (testing "doesn't use '//' for sqlite"
    (let [jdbc-url (z.sql/database-url->jdbcUrl "sqlite://test.db")
          parsed (-> jdbc-url
                     (subs 5)
                     (uri/uri))]
      (is (str/starts-with? jdbc-url "jdbc:"))
      (is (match? {:scheme "sqlite"
                   :host nil
                   :port nil
                   :path "test.db"
                   :query nil
                   :user nil
                   :password nil}
                  (select-keys parsed [:scheme :host :port :path :query :user :password])))))

  (testing "formats port without extra space"
    (let [jdbc-url (z.sql/database-url->jdbcUrl "postgresql://localhost:5432/mydb")]
      (is (str/includes? jdbc-url ":5432"))
      (is (not (str/includes? jdbc-url ": 5432")))))

  (testing "mysql scheme uses // prefix"
    (let [jdbc-url (z.sql/database-url->jdbcUrl "mysql://localhost:3306/mydb")]
      (is (= "jdbc:mysql://localhost:3306/mydb" jdbc-url))))

  (testing "sqlserver scheme uses // prefix"
    (let [jdbc-url (z.sql/database-url->jdbcUrl "sqlserver://localhost:1433/mydb")]
      (is (= "jdbc:sqlserver://localhost:1433/mydb" jdbc-url))))

  (testing "postgres scheme (alternate name) uses // prefix"
    (let [jdbc-url (z.sql/database-url->jdbcUrl "postgres://localhost:5432/mydb")]
      (is (= "jdbc:postgres://localhost:5432/mydb" jdbc-url))))

  (testing "handles minimal url with just scheme and path"
    (let [jdbc-url (z.sql/database-url->jdbcUrl "sqlite:mydb.db")]
      (is (str/starts-with? jdbc-url "jdbc:sqlite:")))))

(deftest execute!
  (is (= [{:? 1}] (z.sql/execute! *db* {:select 1})))
  (is (= [{:1 1}] (z.sql/execute! *db* ["select 1"]))))

(deftest execute-one!
  (is (= {:? 1} (z.sql/execute-one! *db* {:select 1})))
  (is (= {:1 1} (z.sql/execute-one! *db* ["select 1"]))))

(deftest count
  (testing "returns count of results"
    (is (= 1 (z.sql/count *db* {:select [1 2 3]}))))

  (testing "returns 0 when query has no results"
    (is (= 0 (z.sql/count *db* {:select 1 :where [:= 1 0]}))))

  (testing "throws AssertionError when given SQL vector instead of HoneySQL map"
    (is (thrown? AssertionError (z.sql/count *db* ["select 1"])))))

(deftest exists?
  (testing "returns true when query has results"
    (is (= true (z.sql/exists? *db* {:select [1 2 3]}))))

  (testing "returns false when query has no results"
    (is (= false (z.sql/exists? *db* {:select 1 :where [:= 1 0]}))))

  (testing "throws AssertionError when given SQL vector instead of HoneySQL map"
    (is (thrown? AssertionError (z.sql/exists? *db* ["select 1"])))))

(deftest jdbc-options
  (let [label-fn (fn [label]
                   (keyword "xxx" (name label)))
        jdbc-options {:builder-fn jdbc.result-set/as-unqualified-modified-maps
                      :label-fn label-fn}
        config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:"}
                ::z.sql/db {:connectable (ig/ref ::z.sql/pool)
                            :jdbc-options jdbc-options}}
        system (ig/init config)
        db (::z.sql/db system)]
    (try
      (is (= {:xxx/test "test"}
             (z.sql/execute-one! db {:select [["test" :test]]})))
      (finally
        (ig/halt! *system*)))))

(deftest execute-one!-nil-result
  (testing "returns nil when no rows match"
    ;; Create a table, don't insert anything, query for non-existent row
    (z.sql/execute! *db* ["CREATE TABLE test_table (id INTEGER PRIMARY KEY, name TEXT)"])
    (is (nil? (z.sql/execute-one! *db* {:select [:*]
                                        :from [:test-table]
                                        :where [:= :id 999]})))))

(deftest init-function
  (testing "returns function that produces correct config structure"
    (let [init-fn (z.sql/init {:spec {:jdbcUrl "jdbc:sqlite::memory:"}})
          config (init-fn {})]
      (is (contains? config ::z.sql/pool))
      (is (contains? config ::z.sql/db))
      (is (= {:jdbcUrl "jdbc:sqlite::memory:"} (::z.sql/pool config)))
      (is (= (ig/ref ::z.sql/pool) (:connectable (::z.sql/db config))))))

  (testing "uses custom context-key"
    (let [init-fn (z.sql/init {:spec {:jdbcUrl "jdbc:sqlite::memory:"}
                               :context-key :my-custom-db})
          config (init-fn {})]
      (is (= (ig/ref ::z.sql/db)
             (get-in config [:zodiac.core/middleware :context :my-custom-db])))))

  (testing "passes jdbc-options through to ::db"
    (let [opts {:builder-fn jdbc.result-set/as-unqualified-maps}
          init-fn (z.sql/init {:spec {:jdbcUrl "jdbc:sqlite::memory:"}
                               :jdbc-options opts})
          config (init-fn {})]
      (is (= opts (:jdbc-options (::z.sql/db config)))))))

;;; SQLite pragma configuration tests

(defn- create-fk-test-tables
  "Create parent and child tables with foreign key constraint for testing"
  [db]
  (z.sql/execute! db ["CREATE TABLE parent (id INTEGER PRIMARY KEY)"])
  (z.sql/execute! db ["CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER REFERENCES parent(id))"]))

(defn- foreign-keys-enabled?
  "Check if foreign_keys pragma is enabled"
  [db]
  (-> (z.sql/execute-one! db ["PRAGMA foreign_keys"])
      :foreign-keys
      (= 1)))

(defn- fk-violation-throws?
  "Test if inserting a child with non-existent parent throws an error"
  [db]
  (try
    (z.sql/execute! db ["INSERT INTO child (id, parent_id) VALUES (1, 999)"])
    false ;; No error means FK not enforced
    (catch Exception _
      true))) ;; Error means FK is enforced

(deftest sqlite-pragma-via-url-query-params
  (testing "foreign_keys pragma can be set via URL query parameters"
    (let [config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:?foreign_keys=ON"}
                  ::z.sql/db {:connectable (ig/ref ::z.sql/pool)}}
          system (ig/init config)
          db (::z.sql/db system)]
      (try
        (is (foreign-keys-enabled? db)
            "PRAGMA foreign_keys should be ON")
        (create-fk-test-tables db)
        (is (fk-violation-throws? db)
            "Foreign key violation should throw when foreign_keys=ON")
        (finally
          (ig/halt! system))))))

(deftest sqlite-pragma-via-hikari-datasource-properties
  (testing "foreign_keys pragma can be set via HikariCP dataSourceProperties"
    (let [config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:"
                                :dataSourceProperties {:foreign_keys "true"}}
                  ::z.sql/db {:connectable (ig/ref ::z.sql/pool)}}
          system (ig/init config)
          db (::z.sql/db system)]
      (try
        (is (foreign-keys-enabled? db)
            "PRAGMA foreign_keys should be ON via dataSourceProperties")
        (create-fk-test-tables db)
        (is (fk-violation-throws? db)
            "Foreign key violation should throw when foreign_keys=true")
        (finally
          (ig/halt! system))))))

(deftest sqlite-pragma-default-is-disabled
  (testing "foreign_keys pragma is OFF by default in SQLite"
    ;; This test documents the default behavior - FK is disabled
    (let [config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:"}
                  ::z.sql/db {:connectable (ig/ref ::z.sql/pool)}}
          system (ig/init config)
          db (::z.sql/db system)]
      (try
        (is (not (foreign-keys-enabled? db))
            "PRAGMA foreign_keys should be OFF by default")
        (create-fk-test-tables db)
        (is (not (fk-violation-throws? db))
            "Foreign key violation should NOT throw when foreign_keys=OFF")
        (finally
          (ig/halt! system))))))

(deftest sqlite-multiple-pragmas-via-url
  (testing "multiple pragmas can be set via URL query parameters"
    ;; Note: journal_mode=WAL doesn't work with :memory: databases
    ;; Using cache_size and recursive_triggers as examples that work in-memory
    (let [config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:?foreign_keys=ON&cache_size=-2000&recursive_triggers=ON"}
                  ::z.sql/db {:connectable (ig/ref ::z.sql/pool)}}
          system (ig/init config)
          db (::z.sql/db system)]
      (try
        (is (foreign-keys-enabled? db))
        (is (= -2000 (-> (z.sql/execute-one! db ["PRAGMA cache_size"])
                         :cache-size)))
        (is (= 1 (-> (z.sql/execute-one! db ["PRAGMA recursive_triggers"])
                     :recursive-triggers)))
        (finally
          (ig/halt! system))))))

;;; SQLite extension loading tests

(defn- load-extension-error-message
  "Try to load a non-existent extension and return the error message"
  [db]
  (try
    (z.sql/execute! db ["SELECT load_extension('nonexistent_extension')"])
    nil
    (catch Exception e
      (.getMessage e))))

(deftest sqlite-extension-loading-disabled-by-default
  (testing "load_extension is disabled by default for security"
    (let [config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:"}
                  ::z.sql/db {:connectable (ig/ref ::z.sql/pool)}}
          system (ig/init config)
          db (::z.sql/db system)]
      (try
        (let [error-msg (load-extension-error-message db)]
          (is (some? error-msg))
          (is (str/includes? error-msg "not authorized")))
        (finally
          (ig/halt! system))))))

(deftest sqlite-enable-load-extension-via-url
  (testing "enable_load_extension can be set via URL parameter"
    (let [config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:?enable_load_extension=true"}
                  ::z.sql/db {:connectable (ig/ref ::z.sql/pool)}}
          system (ig/init config)
          db (::z.sql/db system)]
      (try
        ;; When enabled, we get "file not found" instead of "not authorized"
        (let [error-msg (load-extension-error-message db)]
          (is (some? error-msg))
          (is (not (str/includes? error-msg "not authorized"))
              "Should not get 'not authorized' when extension loading is enabled"))
        (finally
          (ig/halt! system))))))

(deftest sqlite-enable-load-extension-via-datasource-properties
  (testing "enable_load_extension can be set via dataSourceProperties"
    (let [config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:"
                                :dataSourceProperties {:enable_load_extension "true"}}
                  ::z.sql/db {:connectable (ig/ref ::z.sql/pool)}}
          system (ig/init config)
          db (::z.sql/db system)]
      (try
        (let [error-msg (load-extension-error-message db)]
          (is (some? error-msg))
          (is (not (str/includes? error-msg "not authorized"))
              "Should not get 'not authorized' when extension loading is enabled"))
        (finally
          (ig/halt! system))))))

(deftest sqlite-connection-init-sql
  (testing "connectionInitSql runs on each connection"
    ;; Use connectionInitSql to set a pragma, then verify it took effect
    (let [config {::z.sql/pool {:jdbcUrl "jdbc:sqlite::memory:"
                                :connectionInitSql "PRAGMA cache_size = -5000"}
                  ::z.sql/db {:connectable (ig/ref ::z.sql/pool)}}
          system (ig/init config)
          db (::z.sql/db system)]
      (try
        (is (= -5000 (-> (z.sql/execute-one! db ["PRAGMA cache_size"])
                         :cache-size)))
        (finally
          (ig/halt! system))))))
