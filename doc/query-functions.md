# Query Functions

zodiac-sql provides wrapper functions around next.jdbc that accept either HoneySQL maps or raw SQL vectors.

## execute!

Execute a query and return all results as a sequence of maps.

```clojure
;; With HoneySQL map
(z.sql/execute! db {:select [:id :name]
                    :from [:users]
                    :where [:= :active true]})
;; => [{:id 1 :name "Alice"} {:id 2 :name "Bob"}]

;; With SQL vector
(z.sql/execute! db ["SELECT id, name FROM users WHERE active = ?" true])
;; => [{:id 1 :name "Alice"} {:id 2 :name "Bob"}]
```

## execute-one!

Execute a query and return a single result as a map, or `nil` if no results.

```clojure
(z.sql/execute-one! db {:select [:*]
                        :from [:users]
                        :where [:= :id 1]})
;; => {:id 1 :name "Alice" :email "alice@example.com"}

(z.sql/execute-one! db {:select [:*]
                        :from [:users]
                        :where [:= :id 999]})
;; => nil
```

## exists?

Check if a query returns any results. Returns `true` or `false`.

**Note:** Only accepts HoneySQL maps, not SQL vectors.

```clojure
(z.sql/exists? db {:select [:id]
                   :from [:users]
                   :where [:= :email "alice@example.com"]})
;; => true
```

## count

Count the number of rows a query would return.

**Note:** Only accepts HoneySQL maps, not SQL vectors.

```clojure
(z.sql/count db {:select [:id]
                 :from [:users]
                 :where [:= :active true]})
;; => 42
```

## HoneySQL vs SQL Vectors

All query functions accept either format:

- **HoneySQL maps** - Clojure data structures compiled to SQL via [HoneySQL](https://github.com/seancorfield/honeysql)
- **SQL vectors** - Raw parameterized SQL `["SELECT * FROM users WHERE id = ?" 1]`

The `exists?` and `count` helpers only accept HoneySQL maps because they wrap your query in additional SQL.
