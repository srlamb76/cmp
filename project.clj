(defproject media-player "0.1.0-SNAPSHOT"
  :description "A Clojure-centric media player"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [seesaw "1.5.0"]            ; Clojure wrapper for Swing
                 [javazoom/jlayer "1.0.1"]]  ; For MP3 support
  :main ^:skip-aot media-player.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
