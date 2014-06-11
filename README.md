# cljs-idxdb

Clojurescript wrapper around the indexedDB api found in modern browsers.

Note: this library is in active development, and the api is subject to change from version to version.
See the changelog at the bottom of this readme for information of what has changed.

## Install


Via Clojars: https://clojars.org/cljs-idxdb

[![Current Version](https://clojars.org/cljs-idxdb/latest-version.svg)](https://clojars.org/cljs-idxdb)


## Usage

```clojure
(ns indexeddb.example
  (:require [cljs-idxdb.core :as idx]))

;; An atomic reference to the db object - this could also be a var that you re-root
(def db (atom nil))

(def store-name "example-store")

;; Establish a reference to the db.
;; The first func argument executes if an upgrade is needed
;; The second func argument executes when the database and any upgrades have completed
(idx/create-db "example" 1
                #(-> (idx/delete-and-create-store % store-name {:keyPath "name"})
                     (idx/create-index "ageIndex" "age" {:unique false}))
                #(reset! db %))

;; Add some values to a store
(idx/add-item @db store-name {:name "Jesse" :age 32} #(println "Successfully added Jesse!"))
(idx/add-item @db store-name {:name "Gidget" :age 44} #(println "Successfully added Gidget!"))

;; Get some values
(idx/get-by-key @db store-name "Jesse" (fn [p] (println (:age p))))

(idx/get-by-index @db store-name "ageIndex" 44
  (fn [persons]
   (doseq [person persons] (println (str (:name person) "-" (:age person))))))

```

## Changelog
### 0.1.0
- Initial version.


## License

Copyright © 2014 ICM Consulting Pty Ltd (http://www.icm-consulting.com.au)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
