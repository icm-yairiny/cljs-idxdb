(ns cljs-idxdb.core-test
  (:require [cljs-idxdb.core :refer
             [add-item get-all get-by-key log delete-and-create-store create-index get-by-index open-cursor get-tx-store]]
             [cljs.core.async :refer [put! chan <! pub sub]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cljs-idxdb.core :refer [request-db docursor]]))

(enable-console-print!)

(def db nil)

(defn save-db [{db-obj :db}]
  (println "SUCCESS!")
  (def db db-obj))

(defn get-timestamp []
  (.getTime (js/Date.)))

(defn db-schema [db-ref]
  (println "UPGRADE!")
  (let [store (delete-and-create-store db-ref "todo" {:keyPath "timestamp"})]
    (create-index store "priorityIndex" "priority" {:unique false})))

(defn init-todos []
  (let [[_ ch] (request-db "todos" 12 db-ref (db-schema db-ref))
        success-chan (sub ch :success (chan))
        error-chan (sub ch :error (chan))]
    (go (-> (<! success-chan) save-db))
    (go (-> (<! error-chan) println))))

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


