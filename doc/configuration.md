# Configuration

## init Options

The `zodiac.ext.sql/init` function accepts a map with the following keys:

### :spec

Connection pool configuration passed to [HikariCP](https://github.com/brettwooldridge/HikariCP). Common options:

```clojure
{:jdbcUrl "jdbc:postgresql://localhost:5432/mydb"
 :username "myuser"
 :password "mypassword"
 :maxPoolSize 10}
```

See [HikariCP configuration](https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby) for all available options.

### :jdbc-options

Options for result set handling, passed to next.jdbc. Defaults to `next.jdbc/snake-kebab-opts` which converts `snake_case` column names to `:kebab-case` keywords.

```clojure
(require '[next.jdbc.result-set :as rs])

(z.sql/init {:spec {...}
             :jdbc-options {:builder-fn rs/as-unqualified-maps}})
```

See [next.jdbc options](https://cljdoc.org/d/com.github.seancorfield/next.jdbc/CURRENT/doc/all-the-options) for details.

### :context-key

The key used to store the database connection in the Zodiac request context. Defaults to `:zodiac.ext.sql/db`.

```clojure
;; Use a custom key
(z.sql/init {:spec {...}
             :context-key :my-app/db})

;; Access in handler
(defn handler [{:keys [::z/context]}]
  (let [db (:my-app/db context)]
    ...))
```

## database-url->jdbcUrl

Helper to convert a `DATABASE_URL` environment variable format to a JDBC URL:

```clojure
(z.sql/database-url->jdbcUrl "postgresql://user:pass@localhost:5432/mydb")
;; => "jdbc:postgresql://localhost:5432/mydb?user=user&password=pass"
```

Supports `postgresql`, `postgres`, `mysql`, `sqlserver`, and `sqlite` schemes.

## Date/Time Handling

zodiac-sql automatically reads database timestamps as `java.time.Instant` via `next.jdbc.date-time/read-as-instant`. No configuration needed.

## SQLite Configuration

SQLite requires special handling for [PRAGMA settings](https://sqlite.org/pragma.html) like `foreign_keys`, which are disabled by default.

### Setting Pragmas via URL Query Parameters

The simplest approach is to add pragma settings as query parameters in the JDBC URL:

```clojure
(z.sql/init {:spec {:jdbcUrl "jdbc:sqlite:mydb.db?foreign_keys=ON"}})

;; Multiple pragmas
(z.sql/init {:spec {:jdbcUrl "jdbc:sqlite:mydb.db?foreign_keys=ON&cache_size=-2000&busy_timeout=5000"}})
```

### Setting Pragmas via dataSourceProperties

Alternatively, use HikariCP's `dataSourceProperties` which are passed to the SQLite driver:

```clojure
(z.sql/init {:spec {:jdbcUrl "jdbc:sqlite:mydb.db"
                    :dataSourceProperties {:foreign_keys "true"
                                           :busy_timeout "5000"}}})
```

### Common SQLite Pragmas

| Pragma | Default | Recommended | Description |
|--------|---------|-------------|-------------|
| `foreign_keys` | OFF | ON | Enable foreign key constraint enforcement |
| `journal_mode` | DELETE | WAL | Write-ahead logging for better concurrency |
| `busy_timeout` | 0 | 5000 | Milliseconds to wait when database is locked |
| `synchronous` | FULL | NORMAL | Balance durability vs performance |
| `cache_size` | -2000 | -2000 | Negative = KB, positive = pages |

### Loading SQLite Extensions

SQLite supports [loadable extensions](https://sqlite.org/loadext.html) like [SpatiaLite](https://www.gaia-gis.it/fossil/libspatialite/index) for geospatial queries or [sqlite-vec](https://github.com/asg017/sqlite-vec) for vector search.

Loading extensions requires two steps:

1. **Enable extension loading** (disabled by default for security)
2. **Load the extension** on each connection using `connectionInitSql`

```clojure
(z.sql/init {:spec {:jdbcUrl "jdbc:sqlite:mydb.db?enable_load_extension=true"
                    :connectionInitSql "SELECT load_extension('/path/to/extension')"}})
```

Or using `dataSourceProperties`:

```clojure
(z.sql/init {:spec {:jdbcUrl "jdbc:sqlite:mydb.db"
                    :dataSourceProperties {:enable_load_extension "true"}
                    :connectionInitSql "SELECT load_extension('/path/to/extension')"}})
```

**Why `connectionInitSql`?** HikariCP manages a pool of connections. Extensions must be loaded on each connection, and `connectionInitSql` runs automatically whenever HikariCP creates a new connection.

**Multiple extensions:**

```clojure
{:connectionInitSql "SELECT load_extension('/path/to/ext1'); SELECT load_extension('/path/to/ext2')"}
```
