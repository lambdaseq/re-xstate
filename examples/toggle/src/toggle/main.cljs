(ns toggle.main
  (:require
    [reagent.dom :as rdom]
    [re-frame.core :as re-frame]
    [toggle.rf :as rf :refer [<sub >evt >evt-now]]
    [toggle.views :as views]
    [toggle.config :as config]
    [drbuchkov.re-xstate.core :as rxs]))

(try (require 'hashp.core)
     (catch js/Error _))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root)
    (rdom/render [views/main] root)))

(def toggle-state-machine
  {:id      :toggle-state-machine
   :initial :inactive
   :states  {:inactive {:on {:TOGGLE :active}}
             :active   {:on {:TOGGLE :inactive}}}})

(defn init []
  (>evt-now [::rf/boot])
  (>evt-now [::rxs/initialize-state-machine toggle-state-machine])
  (dev-setup)
  (mount-root))
