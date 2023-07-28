(ns toggle.views
  (:require [toggle.rf :as rf :refer [<sub >evt]]
            [drbuchkov.re-xstate.core :as rxs]))

(def fsm-id "toggle-state-machine")

(defn main []
  (let [{:keys [value] :as state} (<sub [::rxs/fsm-state-map fsm-id])
        {:keys [count]} (<sub [::rxs/fsm-context fsm-id])]
    [:<>
     [:header
      [:h1 "Example using re-xstate"]
      [:p "Current state: " value]
      [:button {:on-click #(>evt [::rxs/send fsm-id
                                  {:type :TOGGLE}])}
       "Toggle"]
      [:p "Toggle Count: " count]
      [:hr]]
     [:main]
     [:footer]]))