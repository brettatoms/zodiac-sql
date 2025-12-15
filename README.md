# Zodiac SQL

[![Clojars Project](https://img.shields.io/clojars/v/com.github.brettatoms/zodiac-sql.svg)](https://clojars.org/com.github.brettatoms/zodiac-sql)
[![cljdoc](https://cljdoc.org/badge/com.github.brettatoms/zodiac-sql)](https://cljdoc.org/d/com.github.brettatoms/zodiac-sql)

An extension for [Zodiac](https://github.com/brettatoms/zodiac) to help with connecting to a SQL database with [next.jdbc](https://github.com/seancorfield/next-jdbc) and execute queries with [HoneySQL](https://github.com/seancorfield/honeysql).

For an example of how to use this extension see [examples/hello-world](examples/hello-world).

### Getting started

```clojure
(ns myapp
  (:require [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [::z.sql/db]} context
        {:keys [message]} (z.sql/execute-one! db {:select [["hello world" :message]]})]
    [:html
     [:head
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1"}]]
     [:body
      message]]))

(defn routes []
  ["/" {:get #'handler}])

(let [sql-ext (z.sql/init {:spec {:jdbcUrl "jdbc:sqlite:hello-world.db"}})]
  (z/start {:extensions [sql-ext]
            :routes #'routes})
```


### Options

The `zodiac.ext.sql/init` accepts the following options:

- `:spec`: The datasource and connection options map.  For more information see [Datasources and Connections]( https://cljdoc.org/d/com.github.seancorfield/next.jdbc/1.3.955/doc/all-the-options#datasources-and-connections) in the next.jdbc docs
- `:jdbc-options`:  The options for building generating SQL and results set.  For more information see [Generating SQL](https://cljdoc.org/d/com.github.seancorfield/next.jdbc/1.3.955/doc/all-the-options#generating-sql) and [Generating Rows and Result Sets](https://cljdoc.org/d/com.github.seancorfield/next.jdbc/1.3.955/doc/all-the-options#generating-rows-and-result-sets) in the next.jdbc docs.
- `:context-key`: The key in the zodiac request context that will hold the database connection.  Defaults to `:zodiac.ext.sql/db`.
