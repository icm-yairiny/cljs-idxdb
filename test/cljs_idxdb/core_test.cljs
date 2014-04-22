(ns cljs-idxdb.core-test
  (:require [cljs-idxdb.core :refer [create-db add-item get-all get-by-key log]]))

(def db nil)

(defn save-db [db-obj]
  (def db db-obj))

(defn get-timestamp []
  (.getTime (js/Date.)))

(defn db-schema [db]
  (when (.. (.-objectStoreNames db) (contains "todo"))
    (.. db (deleteObjectStore "todo")))
  (.. db (createObjectStore "todo" (clj->js {:keyPath "timestamp"}))))


(defn init-todos []
  (create-db "todos" 1 db-schema save-db))

(defn add-todo [txt]
  (add-item db "todo" {:timestamp (get-timestamp) :text txt} log))

(defn print-todos []
  (get-all db "todo" (fn [todos] (doseq [todo todos] (log (:text todo))))))

(defn print-single [timestamp]
  (get-by-key db "todo" timestamp (fn [todo] (log (:text todo)))))
