(ns stardog.core
  (:require [clojure.string :as str]
            [stardog.values :as values])
  (:import [clojure.lang IFn]
           [java.util Map]
           [com.complexible.stardog.api ConnectionConfiguration Connection Query ReadQuery]
           [com.complexible.stardog.reasoning.api ReasoningType]
           [org.openrdf.query TupleQueryResult GraphQueryResult BindingSet Binding]
           [org.openrdf.model URI Literal BNode]
           [info.aduna.iteration Iteration]))

(defn reasoning-type
  ^ReasoningType [r]
  (let [t (str/upper-case (name r))]
    (ReasoningType/valueOf t)))

(defprotocol Connectable
  (connect [c] "Creates a connection with the given parameters"))

(extend-protocol Connectable
  java.util.Map
  (connect
    [{:keys [db user pass server reasoning]}]
    (let [config (ConnectionConfiguration/to db)]
      (when user (.credentials config user pass))
      (when server (.server config server))
      (when reasoning (.reasoning config (reasoning-type reasoning)))
      (.connect config)))
  String
  (connect [cs] (ConnectionConfiguration/at cs)))

(defn as-map
  "Converts a BindingSet into a map."
  [^IFn key-fn ^IFn value-fn ^BindingSet mb]
  (into {} (map (fn [^Binding b] [(key-fn (.getName b)) (value-fn (.getValue b))])
                (iterator-seq (.iterator mb)))))

(defn as-seq [^Iteration i]
  "Converts an Iteration into a lazy-seq"
  (if-not (.hasNext i) nil (cons (.next i) (lazy-seq (as-seq i)))))

(defn key-map-results
  "Converts a Iteration of bindings into a seq of keymaps."
  [^IFn keyfn ^IFn valfn ^Iteration results]
  (let [mapper (partial as-map keyfn valfn)]
    (map mapper (as-seq results))))

(defprotocol ClojureResult
  (clojure-data* [results keyfn valfn]
                 "Typed dispatched conversion of query results into Clojure data"))

(extend-protocol ClojureResult
  GraphQueryResult
  (clojure-data* [results keyfn valfn]
    (let [namespaces (into {} (.getNamespaces results))]
      (with-meta {:namespaces namespaces} (key-map-results keyfn valfn results))))
  TupleQueryResult
  (clojure-data* [results keyfn valfn] (key-map-results keyfn valfn results))
  Boolean
  (clojure-data* [results _ valfn] (valfn results)))

(defn clojure-data
  "Converts query results into Clojure data. Optionally uses functions for interpreting
   names and value bindings in results."
  ([results] (clojure-data* results keyword values/standardize))
  ([results keyfn valfn] (clojure-data* results keyfn valfn)))

(defn execute* [^Query q {:keys [key-converter converter]
                         :or {key-converter keyword converter values/standardize}}]
  (clojure-data (.execute q) key-converter converter))

(defn configure-query
  "Configures a query is the valid parameters for that type of query"
  [^Query q {:keys [parameters reasoning limit offset dataset]}]
  (doseq [[k v] parameters] (.parameter q (name k) v))
  (when dataset (.dataset q dataset))
  (when (instance? ReadQuery q)
    (let [^ReadQuery rq q]
      (when reasoning (.reasoning rq (reasoning-type reasoning)))
      (when limit (.limit rq limit))
      (when offset (.offset rq offset))))
  q)
  
(defn create-query
  "Creates a query using a map of optional arguments.
   new-with-base: Function that creates the query with a base URI.
   new-without-base: Function that creates the query without a base URI.
   args: A map containing any of the following: base, parameters, reasoning, limit, offset"
  ^Query
  [^IFn new-with-base ^IFn new-without-base ^Map {:keys [base] :as args}]
  (let [q (if base (new-with-base base) (new-without-base))]
    (configure-query q args)))

(def query-keys #{:base :parameters :reasoning :limit :offset :converter :key-converter :dataset})

(defn check-arg [pred [f & r :as a]] (if (pred f) [f r] [nil a]))

(defn convert-to-map
  "Converts an arguments array into a map. The arguments are either positional,
   named, or already in map form. This function is a fixpoint."
  [[f & r :as args]]
  (cond
    (and (map? f)
         (= 1 (count args))
         (every? query-keys (keys f))) f
    (keyword? f) (apply map args)
    ;; walk down the arguments and pull them out positionally
    :default (let [[base a] (check-arg string? args)
                   [params a] (check-arg map? a)
                   [reasoning a] (check-arg #(or (string? %) (keyword? %)) a)
                   [converter a] (check-arg fn? a)
                   [key-converter [limit offset]] (check-arg fn? a)]
               (->> {:base base :parameters params :reasoning reasoning
                     :limit limit :offset offset
                     :converter converter :key-converter key-converter}
                    (filter second)
                    (into {})))))

(defn query
  "Executes a query and returns results.
   When constructing a query from text, the parameters are:
   - connection: The connection to query over (required).
   - text: The text of the connection (String - required).
   Remaining argument are optional, and may be positional args,
   a map of args, or named args. Mapped and named args use the keys:
   - base, parameters, reasoning, limit, offset, converter, key-converter
   Positional arguments are in order:
   - base: The base URI for the query (String).
   - parameters: A parameter map to bind parameters in the query (Map).
   - reasoning: The type of reasoning to use with the query (String/keyword).
   - converter: A function to convert returned values with (Function).
   - key-converter: A function to convert returned binding names with (Function).
   - limit: The limit for the result. Must be present to use offset (integer).
   - offset: The offset to start the result (integer)."
  [^Connection connection ^String text & args]
  (let [args (convert-to-map args)
        q (create-query #(.select connection text %) #(.select connection text) args)]
    (execute* q args)))

(defn ask
  "Executes a boolean query.
  Optional parameters may be provided as a map or named parameters.
  Parameter names are:
  - base, parameters, reasoning, limit, offset, converter, key-converter."
  [^Connection connection ^String text & args]
  (let [args (convert-to-map args)
        q (create-query #(.ask connection text %) #(.ask connection text) args)]
    (execute* q args)))

(defn graph
  "Executes a graph query.
  Optional parameters may be provided as a map or named parameters.
  Parameter names are:
  - base, parameters, reasoning, limit, offset, converter, key-converter."
  [^Connection connection ^String text & args]
  (let [args (convert-to-map args)
        q (create-query #(.graph connection text %) #(.graph connection text) args)]
    (execute* q args)))

(defn update
  "Executes an update operation.
  Optional parameters may be provided as a map or named parameters.
  Parameter names are:
  - base, parameters, reasoning, converter."
  [^Connection connection ^String text & args]
  (let [args (convert-to-map args)
        q (create-query #(.update connection text %) #(.update connection text) args)]
    (execute* q args)))

(defn execute
  "Executes a query that has already been created and configured.
   Valid parameters are key-converter and converter. Query configuration
   parameters are ignored."
  [^Query q & args]
  (execute* q (convert-to-map args)))

(defmacro assert-args
  "Duplicates the functionality of the private clojure.core/assert-args"
  [& pairs]
  `(do (when-not ~(first pairs)
         (throw (IllegalArgumentException.
                  (str (first ~'&form) " requires " ~(second pairs) " in " ~'*ns* ":" (:line (meta ~'&form))))))
    ~(let [more (nnext pairs)]
       (when more
         (list* `assert-args more)))))

(defmacro with-transaction
  "(with-transaction [connection...] body)
  Executes the body with a transaction on each of the connections. At completion of the body
  the transaction is committed. If the body fails due to exception, the transaction is rolled back.
  This macro intentionally restricts connections to be symbols, to encourage them to be
  bindings in with-open."
  [connections & body]
  (assert-args
    (vector? connections) "a vector for its connections"
    (every? symbol? connections) "symbols for all connections")
  (let [begins (for [c connections] `(.begin ~c))
        rev (reverse connections)
        commits (for [c rev] `(.commit ~c))
        rollbacks (for [c rev] `(.rollback ~c))]
    `(do
       ~@begins
       (try
         ~@body
         ~@commits
         (catch Throwable t#
           ~@rollbacks
           (throw t#))))))

(defmacro with-connection-tx
  "(with-connection binding-forms body)
   Establishes a connection and a transaction to execute the body within."
  [bindings & body]
  (assert-args
    (vector? bindings) "a vector for its binding"
    (even? (count bindings)) "an even number of forms in binding vector")
  (cond
    (empty? bindings) `(do ~@body)
    (symbol? (bindings 0)) `(let ~(subvec bindings 0 2)
                              (try
                                (with-transaction [~(bindings 0)]
                                  (with-connection-tx ~(subvec bindings 2) ~@body))
                                (finally
                                  (.close ~(bindings 0)))))
    :else (throw (IllegalArgumentException.
                   "with-connection-tx only allows Symbols in bindings"))))

