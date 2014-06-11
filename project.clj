(defproject cljs-idxdb "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2227" :scope "provided"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha" :scope "provided"]]

  :source-paths ["src/clj" "src/cljs"]

  :plugins [[lein-cljsbuild "1.0.3"]]

  :profiles {:dev {:repl-options {:init-ns cljs-idxdb.core}
                   :plugins [[lein-ring "0.8.5"]
                             [com.cemerick/austin "0.1.3"]]
                   :cljsbuild {:builds
                               [{:main { ;; CLJS source code path
                                        :source-paths ["src/cljs/cljs_idxdb"]

                                        ;; Google Closure (CLS) options configuration
                                        :compiler {}}}]}}})
