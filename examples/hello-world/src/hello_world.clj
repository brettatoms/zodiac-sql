(ns hello-world
  (:gen-class)
  (:require [integrant.core :as ig]
            [taoensso.telemere :as t]
            [taoensso.telemere.tools-logging :as tt]
            [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql]
            [next.jdbc :as jdbc]))

;; Logging setup
(add-tap println)
(tt/tools-logging->telemere!) ;; send tools.logging to telemere
(t/set-min-level! :debug)
(t/set-min-level! nil "org.eclipse.jetty.*" :warn)

(defonce ^:dynamic *zodiac* nil)

(defn handler [{:keys [::z/context]}]
  (let [{:keys [db]} context
        {:keys [message]} (z.sql/execute-one! db {:select [["hello world" :message]]})]
    [:html
     [:head
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1"}]]
     [:body
      message]]))

(defn routes []
  ["/" {:name :root
        :handler #'handler}])

(defn -main [& _]
  (let [sql-ext (z.sql/init {:spec {:jdbcUrl "jdbc:sqlite:hello-world.db"
                                    :maxPoolSize 2}})
        options {:extensions [sql-ext]
                 :routes #'routes
                 :reload-per-request? true}]
    (alter-var-root #'*zodiac* (constantly (z/start options)))))

(comment
  (def db "jdbc:sqlite:hello-world.db")
  (require '[next.jdbc :as jdbc])
  (.close (jdbc/get-datasource db))
  (z.sql/execute-one! db {:select [["hello world" :message]]})

  (-> *zodiac* ::z.sql/pool)

  ;; Start
  (-main)

  ;; Stop
  (z/stop  *zodiac*)

  ;; Restart
  (do
    (when *zodiac*
      (z/stop  *zodiac*))
    (-main))
  ())
