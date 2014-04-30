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
  (-> e .-target .-result))

(defn error-callback [e]
  (log "error occurred")
  (log e))

(defn delete-store [db name]
  (when (.. (.-objectStoreNames db) (contains name))
    (.. db (deleteObjectStore name))))

(defn create-store [db name opts]
  (.. db (createObjectStore name (clj->js opts))))

(defn delete-and-create-store [db name opts]
  (delete-store db name)
  (create-store db name opts))

(defn create-index [store name field opts]
  (.. store (createIndex name field (clj->js opts))))

(defn create-db [name version upgrade-fn success-fn]
  (let [request (.open js/indexedDB name version)]
    (set! (.-onsuccess request) (comp success-fn get-target-result))
    (set! (.-onupgradeneeded request) (comp upgrade-fn get-target-result))
    (set! (.-onerror request) error-callback)))

(defn add-item [db store-name item success-fn]
  (when db
    (let [item (clj->js item)
          tx (. db (transaction (clj->js [store-name]) "readwrite"))
          store (. tx (objectStore store-name))
          request (. store (put item))]
      (set! (.-onsuccess request) success-fn))))

(defn make-rec-acc-fn [acc request success-fn]
  (fn [e]
    (if-let [cursor (get-target-result e)]
      (do
        (set! (.-onsuccess request)
              (make-rec-acc-fn
               (conj acc (js->clj (.-value cursor) :keywordize-keys true)) request success-fn))
        (.continue cursor))
      (success-fn acc))))

(defn make-tx
  ([db store-name]
     (make-tx db store-name true))
  ([db store-name readwrite?]
     (. db (transaction (clj->js [store-name]) (if readwrite? "readwrite" "readonly")))))

(defn get-tx-store [db store-name]
  (let [tx (make-tx db store-name)]
    (. tx (objectStore store-name))))

(defn make-range
  ([only]
     (.only js/IDBKeyRange only))
  ([lower? bound open?]
     (if lower?
       (.lowerBound js/IDBKeyRange bound open?)
       (.upperBound js/IDBKeyRange bound open?)))
  ([lower upper open-lower? open-upper?]
     (.bound js/IDBKeyRange lower uppoer open-lower? open-upper?)))

(defn open-cursor [store-or-index range]
  (.openCursor store-or-index range))

(defn get-index [store index-name]
  (.index store index-name))

(defn get-all
  ([db store-name success-fn]
     (get-all db store-name 0 success-fn))
  ([db store-name initial-key success-fn]
     (when db
       (let [store (get-tx-store db store-name)
             range (make-range true initial-key false)
             request (open-cursor store range)]
         (set! (.-onsuccess request) (make-rec-acc-fn [] request success-fn))))))

(defn get-by-key [db store-name key success-fn]
  (when db
    (let [store (get-tx-store db store-name)
          request (. store (get key))]
      (set! (.-onsuccess request) (fn [e] (success-fn (js->clj (get-target-result e) :keywordize-keys true)))))))

(defn get-by-index
  ([db store-name index-name initial-key success-fn]
     (when db
       (let [store (get-tx-store db store-name)
             index (get-index store index-name)
             range (make-range true initial-key false)
             request (open-cursor index range)]
         (set! (.-onsuccess request) (make-rec-acc-fn [] request success-fn))))))
