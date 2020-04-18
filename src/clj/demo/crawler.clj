(ns demo.crawler
  (:require [clojure.set :as set]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [cheshire.core :as json]
            [clj-time.format :as f]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce])
  (:import java.net.URL
           java.net.HttpURLConnection))


;; From datos.gov.co
(defn fetch-file
  [^String d n]
  (let [uri (URL. (str "https://www.datos.gov.co/resource/gt2j-8ykr.json?fecha_de_diagn_stico=" d))
        dest (io/file n)
        conn ^HttpURLConnection (.openConnection ^URL uri)]
    (.connect conn)
    (with-open [is (.getInputStream conn)]
      (io/copy is dest))))

(defn start-crawler
  [max-num]
  (loop [i max-num]
    (when (>= i 0)
      (let [name (clojure.string/join ["temp" i ".json"])
            date (-> i t/days t/ago)
            fmt-str (if (< (t/month date) 4)
                      (if (< (t/day date) 13)
                        "d/M/yy"
                        "dd/M/yyyy")
                      "dd/MM/yyyy")
            str-date (->> date
                          (f/unparse (f/with-zone (f/formatter fmt-str) (t/default-time-zone))))]
        (fetch-file str-date (clojure.string/join ["resources/" name]))
        (recur (dec i))))))

(defn process-data
  [file]
  (let [data (slurp file)]
    (when-not (empty? data)
      (mapv #(mapv % [:id_de_caso
                      :fecha_de_diagn_stico
                      :ciudad_de_ubicaci_n
                      :departamento
                      :atenci_n
                      :edad
                      :sexo
                      :tipo
                      :pa_s_de_procedencia]) (clojure.walk/keywordize-keys (json/parse-string data))))))

(defn parse-json-files
  [max-num header]
  (loop [i max-num
         out [header]]
    (if (>= i 0)
      (let [name (clojure.string/join ["temp" i ".json"])
            data (when (io/resource name)
                   (process-data (io/resource name)))
            res (if (not= (count data) 0)
                  (concat out data)
                  out)]
        (recur (dec i) res))
      out)))

;; look for bad dates formats and fix it under our json
(defn standarize-dates
  [max-num]
  (loop [i max-num
         out 0]
    (if (>= i 0)
      (let [fmt (f/formatter "dd/MM/yyyy")
            name (clojure.string/join ["temp" i ".json"])
            body (slurp (io/resource name))
            json-obj (json/parse-string body)
            dat ((first json-obj) "fecha_de_diagn_stico")
            vdat (clojure.string/split dat #"/")
            new-date (if (and (count (nth vdat 2))
                              (= (nth vdat 2) "20"))
                       (f/unparse fmt (f/parse (f/formatter "dd/MM/yy") dat))
                       (f/unparse fmt (f/parse (f/formatter "dd/MM/yyyy") dat)))
            new-data (clojure.walk/prewalk-replace {["fecha_de_diagn_stico" dat] ["fecha_de_diagn_stico" new-date]} json-obj)
            _ (if-not (nil? new-data)
                (spit (str "resources/" name) (json/encode new-data)))]
        (recur (dec i) (+ out (count json-obj))))
      out)))

(def fmt (f/formatter "dd/MM/yyyyy"))
(def json-data (->> "datos.json"
                    io/resource
                    slurp
                    json/parse-string))
(def start-date (->> (json-data "data")
                     first
                     rest
                     vec
                     (map second)
                     first
                     (f/parse fmt)))

(def days-diff (- (t/in-days (t/interval start-date (t/now))) 1))

(def header ["ID de caso"
             "Fecha de diagnóstico"
             "Ciudad de ubicación"
             "Departamento o Distrito"
             "Atención**"
             "Edad"
             "Sexo"
             "Tipo*"
             "País de procedencia"])

;; create new datos.json file
(do
  (start-crawler days-diff)
  (standarize-dates days-diff)
  (let [content (parse-json-files days-diff header)
        sheet-names ["PositivasNegativas"
                       "Titulo"
                       "Casos1"
                       "IndicadoresGenerales"
                       "Mapa"
                       "Copia de Mapa"
                       "Mundo"
                       "Procesadas"
                       "PCR"
                       "Etario"
                       "TotalEtario"
                       "importadosvsrelacionados"
                       "Procedencia"
                       "Estado"
                       "Estado2"
                       "Hoja 12"
                       "Historico_Muestras"
                       "fys"
                       "Laboratorios operando en Colomb"]
          data-json (when content
                      {:data [content]
                       :sheetNames ["Casos1"]
                       :allSheetNames sheet-names
                       :refreshed (coerce/to-long (t/now))})]
      (spit "resources/datos.json" (json/encode data-json))))

;; export to csv
(let [json-data (->> "datos.json"
                      io/resource
                      slurp
                      json/parse-string)
      data (json-data "data")
      last-date (->> data
                     first
                     rest
                     (map second)
                     last)
      date (clojure.string/replace last-date #"/" "-")
      file-name (clojure.string/join ["data/" "Datos_" date ".csv"])]
  (spit file-name "" :append false)
  (with-open [out-file (io/writer file-name)]
    (csv/write-csv out-file (first data))))