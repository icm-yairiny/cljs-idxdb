(ns cljs-idxdb.core)


(defmacro request-db
  "Request a reference to a db. If an upgrade is needed, executes body within the version change transaction.
  Returns a tuple with the request, and a subscribable chan, that publishes to the following topics on events:
  - :success
  - :error"
  [name version db-binding & body]
  `(let [db-callback-chan# (cljs.core.async/chan)
        publication-chan# (cljs.core.async/pub db-callback-chan# :topic)
        request# (.open js/indexedDB ~name ~version)]
    (set! (.-onupgradeneeded request#) (fn [ev#] (let [~db-binding (cljs-idxdb.core/get-target-result ev#)] ~@body)))
    (set! (.-onsuccess request#) (cljs-idxdb.core/handle-callback-chan db-callback-chan# request# :success))
    (set! (.-onerror request#) (cljs-idxdb.core/handle-callback-chan db-callback-chan# request# :error))
    [request# publication-chan#]))


(defmacro docursor
  "Like doseq, but iterates over the bound cursor.
  Repeatedbly executes body for each item returned by cursor.
  Returns nil"
  [[bind-sym cursor-req] & body]
  `(let [step# (fn [~bind-sym] (do ~@body))
         iter-cur# ~cursor-req]
      (set! (.-onsuccess iter-cur#)
            (fn [ev#]
              (when-let [cur# (cljs-idxdb.core/get-target-result ev#)]
                (do
                  (step# (cljs.core/js->clj (.-value cur#)))
                  (.continue cur#)))))))

