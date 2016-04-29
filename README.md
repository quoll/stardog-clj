# stardog

A Clojure wrapper for the Stardog API.
  `http://stardog.com/`

This is still a work in progress.

## Usage

To execute a SPARQL Query:

```
=> (use 'stardog.core)
=> (def c (connect {:db "my-database" :server "snarl://localhost"}))
=> (def results (query c "select ?n { .... }"))
=> (take 5 results)
({:n #<StardogURI http://mulgara.org/math#2>} {:n #<StardogURI http://mulgara.org/math#3>} {:n #<StardogURI http://mulgara.org/math#5>} {:n #<StardogURI http://mulgara.org/math#7>} {:n #<StardogURI http://mulgara.org/math#11>})

=> (def string-results (query c "select ?n { .... }" {:converter str}))
=> (take 5 string-results)
({:n "http://mulgara.org/math#2"} {:n "http://mulgara.org/math#3"} {:n "http://mulgara.org/math#5"} {:n "http://mulgara.org/math#7"} {:n "http://mulgara.org/math#11"})
```

There are wrappers for:
 * connect
 * query
 * update
 * ask
 * graph

Most query options are available for configuring as keys in the parameter map. When requesting
reasoners, use strings or keywords.

### Query Results

Results from SPARQL queries are lazy sequences of bindings from variable names to values.
By default, variable names are converted to keywords, and values are left untouched. This can
be changed by providing functions for the :key-converter and :converter parameters.

Graph results are the same as query results, with namespaces attached as metadata on the entire
sequence (not on sub-sequences).

### Transactions

While there are no update api wrappers yet, there is a macro for dealing with transactions:

```
(with-open [c (connect {:db "my-database" :server "snarl://localhost"})]
  (with-transaction [c]
    (.. c
        (add)
        (io)
        (format RDFFormat/N3)
        (stream (input-stream "data.n3")))))
```

## License

Copyright © 2014 Paula Gearon

Distributed under the Eclipse Public License 1.0, the same as Clojure.
