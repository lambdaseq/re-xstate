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

(def toggle-state-machine-config
  {:id      "toggle-state-machine"
   :initial :inactive
   :context {:count 0}
   :states  {:inactive {:on {:TOGGLE {:target  :active
                                      :actions [:increment]}}}
             :active   {:on {:TOGGLE :inactive}}}})

(def toggle-state-machine-options
  {:actions {:increment (rxs/wrap-ctx-action
                          (fn [{:keys [ctx] :as evt}]
                            (update ctx :count inc)))
             :log-ctx   (rxs/wrap-effectful-action
                          (fn [{:keys [ctx]}]
                            #p ctx))}})

(defn init []
  (>evt-now [::rf/boot])
  (>evt-now [::rxs/initialize-fsm
             toggle-state-machine-config
             toggle-state-machine-options])
  (dev-setup)
  (mount-root))
