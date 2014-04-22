(ns cljs-idxdb.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put! chan <!]]))

(defn pprint [o]
  (println (JSON/stringify o nil 2)))

(defn log [v & text]
  (let [vs (if (string? v)
             (apply str v text)
             v)]
    (. js/console (log vs))
    v))

(defn get-target-result [e]
  (.-result (.-target e)))

(defn create-db [name version upgrade-fn success-fn]
  (let [request (.open js/indexedDB name version)]
    (set! (.-onsuccess request) (comp success-fn get-target-result))
    (set! (.-onupgradeneeded request) (comp upgrade-fn get-target-result))))

(defn add-item [db store-name item success-fn]
  (when db
    (let [item (clj->js item)
          tx (. db (transaction (clj->js [store-name]) "readwrite"))
          store (. tx (objectStore store-name))
          request (. store (put item))]
      (set! (.-onsuccess request) success-fn))))

(declare make-rec-acc-fn)

(defn make-rec-acc-fn [acc request success-fn]
  (fn [e]
    (if-let [cursor (get-target-result e)]
      (do
        (set! (.-onsuccess request)
              (make-rec-acc-fn
               (conj acc (js->clj (.-value cursor) :keywordize-keys true)) request success-fn))
        (.continue cursor))
      (success-fn acc))))

(defn get-all [db store-name success-fn]
  (when db
    (let [tx (. db (transaction (clj->js [store-name]) "readwrite"))
          store (. tx (objectStore store-name))
          range (.lowerBound js/IDBKeyRange 0)
          request (. store (openCursor range))]
      (set! (.-onsuccess request) (make-rec-acc-fn [] request success-fn)))))
