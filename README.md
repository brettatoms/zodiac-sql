# Zodiac SQL

[![Clojars Project](https://img.shields.io/clojars/v/com.github.brettatoms/zodiac-sql.svg)](https://clojars.org/com.github.brettatoms/zodiac-sql)

An extension for [Zodiac](https://github.com/brettatoms/zodiac) to help with connecting to a SQL database with [next.jdbc](https://github.com/seancorfield/next-jdbc) and execute queries with [HoneySQL](https://github.com/seancorfield/honeysql).

For an example of how to use this extension see [examples/hello-world](examples/hello-world).

### Getting started

```clojure
(ns myapp
  (:require [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql]))

(defn handler [{:keys [::z/context]}]
  ;; The assets function in the request context can be used
  ;; to get the url path to built assets from the Vite manifest.
  (let [{:keys [db]} context]
    [:html
     [:head
       [:script {:src (assets "src/myapp.ts")}]]
     [:body
     (let [result])
     (z.sql/execute-one! db {:select "hello world"})
       [:div "hello world"]]]))

(defn routes []
  ["/" {:get #'handler}])

(let [sql-ext (z.sql/init {spec {}
                                 ;; so it needs to be an absolute path on the
                                 ;; filesystem, e.g. not in a jar.
                                 :config-file (str (fs/path project-root "vite.config.js"))
                                 ;; The manifest path is the relative resource
                                 ;; path to the output manifest file. This value doesn't override the build
                                 ;; time value for the output path of the manifest file.  By default
                                 ;; the manifest file is written to <outDir>/.vite/manifest.json
                                 :manifest-path  "myapp/.vite/manifest.json"
                                 ;; The resource path the the built assets. By default the build assets
                                 ;; are written to  <outDir>/assets
                                 :asset-resource-path "myapp/assets"})]
  (z/start {:extensions [assets-ext]
            :routes routes})

```


### Options

The `zodiac.ext.sql/init` accepts the following options:

- `:spec`: The datasource and connection options map.  For more information see [Datasources and Connections]( https://cljdoc.org/d/com.github.seancorfield/next.jdbc/1.3.955/doc/all-the-options#datasources-and-connections) in the next.jdbc docs
- `:jdbc-options`:  The options for building generating SQL and results set.  For more information see [Generating SQL](https://cljdoc.org/d/com.github.seancorfield/next.jdbc/1.3.955/doc/all-the-options#generating-sql) and [Generating Rows and Result Sets](https://cljdoc.org/d/com.github.seancorfield/next.jdbc/1.3.955/doc/all-the-options#generating-rows-and-result-sets) in the next.jdbc docs.
- `:context-key`: The key in the zodiac request context that will hold the database connection.  Defaults to `:db`.
