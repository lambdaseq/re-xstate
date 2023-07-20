(ns toggle.views
  (:require [toggle.rf :as rf :refer [<sub >evt]]
            [drbuchkov.re-xstate.core :as rxs]))

(defn main []
  (let [{:keys [value] :as state} (<sub [::rxs/state-machine-state :toggle-state-machine])]
    [:<>
     [:header
      [:h1 "Example using re-xstate"]
      [:p "Current state: " value]
      [:button {:on-click #(>evt [::rxs/send :toggle-state-machine {:type :TOGGLE}])}
       "Toggle"]
      [:hr]]
     [:main]
     [:footer]]))