(ns cljs-idxdb.atom-sync
  (:require [cljs-idxdb.core :as idx]))


(defn atom>>object-store
  "Keep the-atom and the indexdb object-store in sync when making changes to the atom (one way).
  i.e when changes are made via swap! or reset! to the atom, the same changes will be
  made to the object store.
  Only synchronises the value(s) from the atom found by get-in path. Returns a keyword that can be used
  for stopping the synchronisation with stop-atom>>object-store.
  If the path resolves to a list/vector/set/seq, then all the items at that path will be stored in the object store individually.
  However, if an item is removed from the collection, it WILL NOT be removed from the object store."
  [db object-store-name the-atom path]
  (let [key (keyword (str object-store-name "." (idx/key-path->str path)))]
      (add-watch the-atom key (fn [_ _ old-state new-state]
                                (when-not (= (get-in old-state path)
                                             (get-in new-state path))
                                  (let [new-val (get-in new-state path)
                                        new-val (if (map? new-val) [new-val] new-val)]
                                    (doseq [item new-val]
                                      (idx/add-item db object-store-name item))))))
    key))

(defn stop-atom>>object-store
  "Stop synchronising the-atom with the indexed-db object store"
  [the-atom kw]
  (remove-watch the-atom kw))

