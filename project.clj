(defproject cljs-idxdb "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [cljs-defasync "0.1.0-SNAPSHOT"]]
  :source-paths ["src/clj" "src/cljs"]
  
  :profiles {:dev {:repl-options {:init-ns cljs-idxdb.core}
                   :plugins [[lein-ring "0.8.5"]
                             [com.cemerick/austin "0.1.3"]
                             [lein-cljsbuild "1.0.2"]]
                   :cljsbuild {:builds
                               {:main { ;; CLJS source code path
                                       :source-paths ["src/cljs/cljs_idxdb"]

                                       ;; Google Closure (CLS) options configuration
                                       :compiler {}}}}}})
