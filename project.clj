(defproject netman "1.0.0-SNAPSHOT"
  :description "A personal tool for managing wifi network data"
  :shell-wrapper {:main netman.core
                  :bin "bin/netman"}

  :dependencies [[org.clojure/clojure "1.2.1"]
		[org.clojure/java.jdbc "0.3.0-alpha4"]
		[org.xerial/sqlite-jdbc "3.7.2"]])
