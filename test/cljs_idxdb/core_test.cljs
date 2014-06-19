(ns cljs-idxdb.core-test
  (:require [cljs-idxdb.core :refer
             [add-item get-all get-by-key log delete-and-create-store create-index get-by-index
              open-cursor get-tx-store make-range get-by-index-range get-all-from]]
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
  (let [store (delete-and-create-store db-ref "todo" :key-path "timestamp")]
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
   (let [publ (add-item db "todo" {:timestamp (get-timestamp) :text txt :priority priority})
         success-ch (sub publ :success (chan))]
     (go (-> (<! success-ch) log)))))

(defn print-todos []
  (go (doseq [todo (<! (get-all db "todo" :keywordize-keys true))]
        (log (str (:priority todo) "-" (:text todo))))))

(defn print-single [timestamp]
  (let [result-ch (get-by-key db "todo" timestamp :keywordize-keys true)]
    (go (log (:text (<! result-ch))))))

(defn print-priorities [priority]
  (let [ch (get-by-index db "todo" "priorityIndex" priority :keywordize-keys true)]
    (go (doseq [todo (<! ch)]
          (log (str (:priority todo) "-" (:text todo)))))))

