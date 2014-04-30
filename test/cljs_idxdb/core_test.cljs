(ns cljs-idxdb.core-test
  (:require [cljs-idxdb.core :refer
             [create-db add-item get-all get-by-key log delete-and-create-store create-index get-by-index]]))

(def db nil)

(defn save-db [db-obj]
  (def db db-obj))

(defn get-timestamp []
  (.getTime (js/Date.)))

(defn db-schema [db]
  (let [store (delete-and-create-store db "todo" {:keyPath "timestamp"})]
    (create-index store "priorityIndex" "priority" {:unique false})))

(defn init-todos []
  (create-db "todos" 3 db-schema save-db))

(defn add-todo
  ([txt]
     (add-todo txt 1))
  ([txt priority]
     (add-item db "todo" {:timestamp (get-timestamp) :text txt :priority priority} log)))

(defn print-todos []
  (get-all db "todo" (fn [todos] (doseq [todo todos] (log (:text todo))))))

(defn print-single [timestamp]
  (get-by-key db "todo" timestamp (fn [todo] (log (:text todo)))))

(defn print-priorities [priority]
  (get-by-index db "todo" "priorityIndex" priority (fn [todos]
                                                     (doseq [todo todos] (log (str (:priority todo) "-" (:text todo)))))))
