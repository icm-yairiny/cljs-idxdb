(ns cljs-idxdb.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [chan <! >! put! sub pub]]))

(defn pprint [o]
  (println (.stringify js/JSON o nil 2)))

(defn init-indexedDb-var
  []
  (set! (.-indexedDB js/window)
       (or (.-indexedDB js/window)
           (.-mozIndexedDB js/window)
           (.-webkitIndexedDB js/window)
           (.-msIndexedDB js/window)))
  (set! (.-IDBKeyRange js/window)
       (or (.-IDBKeyRange js/window)
           (.-webkitIDBKeyRange js/window)
           (.-msIDBKeyRange js/window))))

(defn log [v & text]
  (let [vs (if (string? v)
             (apply str v text)
             v)]
    (. js/console (log vs))
    v))

(defn has-name?
  "Can we apply name on o?"
  [o]
  (or (symbol? o) (string? o) (keyword? o)))

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

(defn key-path->str
  [kp]
  (->>
   (if (vector? kp) kp [kp])
   (map #(if (has-name? %) (name %) (str %)))
   (clojure.string/join ".")))


(defn create-store
  "If the requested store does not exist, creates it in the given db.
  Returns the store reference.
  Takes the following options:
  - :key-path - the path to the key for the objects in the store, either as a single key name, or as a vector that describes a path to the key
  - :auto-increment - does the object store have a key generator?"
  [db store-name & {:as opts}]
  (let [key-path (:key-path opts [])
        key-path-str (key-path->str key-path)]
    (.. db (createObjectStore store-name #js {:keyPath key-path-str :autoIncrement (:auto-increment opts)}))))


(defn delete-and-create-store [db name & {:as opts}]
  (delete-store db name)
  (apply create-store db name opts))

(defn create-index [store name field opts]
  (.. store (createIndex name field (clj->js opts))))


(defn handle-callback-chan
  [ch request topic]
  (fn [e]
    (put! ch {:topic topic :db (get-target-result e)})))

(defn- do-read-write-store-action
  "Perform a an action that requires read write access to the given store. Returns a publication
  for subscribing to either a success or error topic."
  [db store-name store-action-fn]
  (when db
    (let [result-ch (chan)
          publication (pub result-ch :topic)
          tx (. db (transaction (clj->js [store-name]) "readwrite"))
          store (. tx (objectStore store-name))
          request (store-action-fn store)]
      (set! (.-onsuccess request) (handle-callback-chan result-ch request :success))
      (set! (.-onerror request) (handle-callback-chan result-ch request :error))
      publication)))

(defn add-item
  "Add the given item to the given store. The item should be a clojure construct, and will be converted to a
  javascript object prior to being stored. Returns a pub that can be used to subscribe to the following topics:
  - :success
  - :error"
  [db store-name item]
  (let [item (clj->js item)]
    (do-read-write-store-action db store-name #(. % (put item)))))

(defn remove-item
  "Remove the item from the given store that matches the item-key value.
  Returns a pub that can be used to subscribe to the following topics:
  - :success
  - :error"
  [db store-name item-key]
  (do-read-write-store-action db store-name #(. % (delete item-key))))

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
  Returns a chan that will have the result put on it. If no items are found, puts false on the chan."
  [db store-name key & {:keys [keywordize-keys] :as opts}]
  (when db
    (let [result-ch (chan 1)
          store (get-tx-store db store-name)
          request (. store (get key))]
      (set! (.-onsuccess request)
            (fn [e]
              (let [result (or (js->clj (get-target-result e) :keywordize-keys keywordize-keys) false)]
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
