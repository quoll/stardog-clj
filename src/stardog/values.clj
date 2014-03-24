(ns stardog.values
  (:import [org.openrdf.model URI Literal BNode]))

(defmulti typed-value (fn [^Literal v] (str (.getDatatype v))))

(defmethod typed-value "http://www.w3.org/2001/XMLSchema#integer"
  [^Literal v] (.intValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#boolean"
  [^Literal v] (.booleanValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#byte"
  [^Literal v] (.byteValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#dateTime"
  [^Literal v] (.calendarValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#time"
  [^Literal v] (.calendarValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#date"
  [^Literal v] (.calendarValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gYearMonth"
  [^Literal v] (.calendarValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gMonthYear"
  [^Literal v] (.calendarValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gYear"
  [^Literal v] (.calendarValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gMonth"
  [^Literal v] (.calendarValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#gDay"
  [^Literal v] (.calendarValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#decimal"
  [^Literal v] (.decimalValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#double"
  [^Literal v] (.doubleValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#float"
  [^Literal v] (.floatValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#long"
  [^Literal v] (.longValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#short"
  [^Literal v] (.shortValue v))
(defmethod typed-value "http://www.w3.org/2001/XMLSchema#string"
  [^Literal v] (.getLabel v))
(defmethod typed-value :default
  [^Literal v]
  (let [lang (.getLanguage v)
        label (.getLabel v)]
    (if lang
      {:lang lang :value label}
      (if-let [dt (.getDatatype v)]
        {:datatype dt :value label}
        label))))

(defprotocol ClojureConverter
  (standardize [v] "Standardizes a value into something Idiomatic for Clojure"))

(extend-protocol ClojureConverter
  org.openrdf.model.URI
  (standardize [v] (java.net.URI. (str v)))
  Literal
  (standardize [v] (typed-value v))
  BNode
  (standardize [v] (keyword "_" (str "b" (.getID v)))))

