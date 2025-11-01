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
                   :path "test.dn"
                   :query nil
                   :user nil
                   :password nil}
                  (select-keys parsed [:scheme :host :port :path :query :user :password]))))))

(deftest execute!
  (is (= [{:? 1}] (z.sql/execute! *db* {:select 1})))
  (is (= [{:1 1}] (z.sql/execute! *db* ["select 1"]))))

(deftest execute-one!
  (is (= {:? 1} (z.sql/execute-one! *db* {:select 1})))
  (is (= {:1 1} (z.sql/execute-one! *db* ["select 1"]))))

(deftest count
  (is (= 1 (z.sql/count *db* {:select [1 2 3]}))))

(deftest exists?
  (is (= true (z.sql/exists? *db* {:select [1 2 3]}))))

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
