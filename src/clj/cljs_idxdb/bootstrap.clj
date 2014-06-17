(ns cljs-idxdb.bootstrap
  (:require [cemerick.austin.repls :as repls]))

(defn cljs-start []
  (repls/exec :exec-cmds ["open" "-ga" "/Applications/Google Chrome.app"]))



