; -*- lisp -*-

; This is the first thing I ever wrote in Clojure; I have no idea
; if the code is clean / idiomatic, etc

(ns netman.core
	(:use [clojure.java.jdbc :only [with-connection with-query-results]]))

(use '[clojure.string :only (split triml)])
(use '[clojure.java.shell])

(def db
	 {
	 :classname "org.sqlite.JDBC"
	 :subprotocol "sqlite"
	 :subname "/home/mk270/.wifi.db"
	 })

(defn keyword-format [keyword n] (str keyword " " n))
(defn wifi-name [n] (format "wpa-ssid %s" n))
(defn wifi-passwd [p] (format "wpa-passphrase %s" p))
(defn wep-name [n] (format "wireless-essid %s" n))
(defn wep-passwd [p] (format "wireless-key1 %s" p))
(defn gateway [n] (keyword-format "gateway" n))
(defn netmask [n] (keyword-format "netmask" n))
(defn address [n] (keyword-format "address" n))

(defn wpa [n password] [(wifi-name n) (wifi-passwd password)])
(defn wep [n password] [(wep-name n) (wep-passwd password)])

(defn with-tab [s] (str "\t" s "\n"))

(defn interface [n auto type attributes]
  (let
	  [auto-line (if auto ["auto " n "\n"] ())
	   iface-line ["iface " n " inet " type "\n"]
	   attributes-line (map with-tab attributes)
	  ]
	  (apply str (flatten [auto-line iface-line attributes-line "\n"]))
	)
  )

(defn disassemble-sqlite-record [r]
	 [(get r :tag) (get r :type) (get r :essid) (get r :passphrase)])

(defn wifi-networks []
  (with-connection db
    (with-query-results rs ["select * from wifi_net"]
						(map disassemble-sqlite-record (doall rs)))))

(defn essid-and-tag [i] [(nth i 2) (nth i 0)])

(def wifi-types (hash-map "wpa" wpa "wep" wep))

(defn make-network [network]
  (let
	  [details (rest network)
	  type (first details)
	  type (get wifi-types type)
	  name-and-password (rest details)]
	  
	  [(first network) (flatten [type name-and-password])]
))

(def networks
	 (apply hash-map
	  (apply concat (map make-network (wifi-networks)))
	  )
)

(defn list-networks [n]
  (doseq [i (keys n)] (println i)))

(defn interfaces [network]
  (apply str [
			  (interface "lo" true "loopback" ())
			  (interface "eth0" false "dhcp" 
						 [ (address "81.2.77.251")
						 (netmask "255.255.255.248")
						 (gateway "81.2.77.249") ])
			  (interface "wlan0" true "dhcp"
						 (let [nw (get networks network)
							  type (first nw)
							  name (first (rest nw))
							  pass (first (rest (rest nw)))]
							  nw (type name pass)))]))
							  

(defn set-location [location]
  (if (get networks location)
	  (println (interfaces location))
	(do (println (format "Location: `%s' not found" location))
		(System/exit 1))))

(defn extract-essid [essid-line]
  (let [token "ESSID:"
	   token-pos (.indexOf essid-line token)
	   token-len (.length token)
	   essid-quoted (.substring essid-line (+ token-len token-pos))
	   essid-quoted-len (.length essid-quoted)
	   ]
	   (.substring essid-quoted 1 (- essid-quoted-len 1))))

; allow printing of networks
(defn infer-network []
  (let [scan (get (clojure.java.shell/sh "iwlist" "scan") :out)
	   wifi-net-data (wifi-networks)
	   essid-lines (filter (fn [x] (.contains x "ESSID:")) (.split scan "\n"))
	   essids (map extract-essid essid-lines)
	   essids-and-tags (map essid-and-tag wifi-net-data)
	   essid-map (apply hash-map (apply concat 
										(map essid-and-tag wifi-net-data)))
	   available (filter #(not (nil? %)) (map essid-map essids))
	   ]
	   (shutdown-agents)
	   (first available)))

(defn usage []
  (println "Usage: netman network-name"))

(defn -main [& args]
  (let [location (first args)]
	   (cond
		(= location "list") (list-networks networks)
		(= location "auto") (println (infer-network))
		(not location)      (usage)
		:else (set-location location))))
