(ns cljs-idxdb.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [chan <! >! put! sub pub]]))

(defn pprint [o]
  (println (.stringify js/JSON o nil 2)))

(defn log [v & text]
  (let [vs (if (string? v)
             (apply str v text)
             v)]
    (. js/console (log vs))
    v))

(defn get-target-result [e]
  (when e
    (-> e .-target .-result)))

(defn error-callback [e]
  (log "error occurred")
  (log e))

(defn delete-store [db name]
  (println db name (.-objectStoreNames db))
  (when (.. (.-objectStoreNames db) (contains name))
    (.. db (deleteObjectStore name))))

(defn create-store [db name opts]
  (.. db (createObjectStore name (clj->js opts))))

(defn delete-and-create-store [db name opts]
  (delete-store db name)
  (create-store db name opts))

(defn create-index [store name field opts]
  (.. store (createIndex name field (clj->js opts))))


(defn handle-callback-chan
  [ch request topic]
  (fn [e]
    (put! ch {:topic topic :db (get-target-result e)})))


(defn add-item
  "Add the given item to the given store. The item should be a clojure construct, and will be converted to a
  javascript object prior to being stored. Returns a pub that can be used to subscribe to the following topics:
  - :success
  - :error"
  [db store-name item]
  (when db
    (let [result-ch (chan)
          publication (pub result-ch :topic)
          item (clj->js item)
          tx (. db (transaction (clj->js [store-name]) "readwrite"))
          store (. tx (objectStore store-name))
          request (. store (put item))]
      (set! (.-onsuccess request) (handle-callback-chan result-ch request :success))
      (set! (.-onerror request) (handle-callback-chan result-ch request :error))
      publication)))


(defn make-rec-acc-fn [acc request result-ch keywordize-keys?]
  (fn [e]
    (if-let [cursor (get-target-result e)]
      (do
        (set! (.-onsuccess request)
              (make-rec-acc-fn
               (conj acc (js->clj (.-value cursor) :keywordize-keys keywordize-keys?))
               request result-ch keywordize-keys?))
        (.continue cursor))
      (put! result-ch acc))))

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
     (.bound js/IDBKeyRange lower upper open-lower? open-upper?)))

(defn open-cursor
  ([store-or-index]
   (.openCursor store-or-index))
  ([store-or-index range]
   (.openCursor store-or-index range)))

(defn get-index [store index-name]
  (.index store index-name))

(defn get-all-from
  "Get all items in a store, starting at the given key. Returns a chan that will receive the results"
  [db store-name initial-key & {:keys [keywordize-keys] :as opts}]
  (when db
    (let [result-ch (chan)
          store (get-tx-store db store-name)
          range (make-range true initial-key false)
          request (open-cursor store range)]
      (set! (.-onsuccess request) (make-rec-acc-fn [] request result-ch keywordize-keys))
      result-ch)))

(defn get-all
  "Get all items in a store, returning a chan that will have the values put on it once available."
  ([db store-name & {:keys [keywordize-keys] :as opts}]
     (get-all-from db store-name 0 :keywordize-keys keywordize-keys)))

(defn get-by-key
  "Search for an item in the given object store by the configured key.
  Returns a chan that will have the result put on it. If no items are found, puts :not-found on the chan."
  [db store-name key & {:keys [keywordize-keys] :as opts}]
  (when db
    (let [result-ch (chan 1)
          store (get-tx-store db store-name)
          request (. store (get key))]
      (set! (.-onsuccess request)
            (fn [e]
              (let [result (or (js->clj (get-target-result e) :keywordize-keys keywordize-keys)
                               :not-found)]
                (put! result-ch result))))
      result-ch)))

(defn get-by-index-range
  "Search for items in the given object store by the given range in the given index.
  Returns a chan that will have the results as a seq put on it."
  ([db store-name index-name range & {:keys [keywordize-keys] :as opts}]
     (when db
       (let [result-ch (chan 1)
             store (get-tx-store db store-name)
             index (get-index store index-name)
             request (open-cursor index range)]
         (set! (.-onsuccess request) (make-rec-acc-fn [] request result-ch keywordize-keys))
         result-ch))))

(defn get-by-index
  "Search for items in the given object store by the given value in the given index.
  Returns a chan that will have the results as a seq put on it."
  ([db store-name index-name value & {:keys [keywordize-keys] :as opts}]
     (get-by-index-range db store-name index-name (make-range value) :keywordize-keys keywordize-keys)))
