(ns frontend.core
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.dom :as rd]
            [frontend.events :as events]
            [frontend.pages :as pages :refer [home]]))

(defn start []
  (rd/render [home]
             (. js/document (getElementById "app"))))

(defn stop []
  ;; stop is called before any code is reloaded
  ;; this is controlled by :before-load in the config
  (.log js/console "stop"))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (re-frame/dispatch-sync [::events/initialize-db])
  (re-frame/dispatch-sync [::events/load-data])
  (start))
