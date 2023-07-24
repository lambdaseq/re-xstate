(ns drbuchkov.re-xstate.core
  (:require [re-frame.core :as rf]
            ["xstate" :as xs]))

(defn get-fsm
  "Returns the FSM by id from db."
  [db [_ fsm-id]]
  (get-in db [::fsm fsm-id]))

(rf/reg-sub
  ::fsm
  get-fsm)

(defn get-fsm-state
  "Returns the current state as a js object (returned by XState) of the FSM from db."
  [db [_ fsm-id]]
  (-> db
      (get-fsm [_ fsm-id])
      :state))

(rf/reg-sub
  ::fsm-state
  get-fsm-state)

(defn normalize-actions
  "Normalizes the actions by converting the type to a keyword."
  [actions]
  (->> actions
       (map #(-> %
                 (update :type keyword)))))

(defn xstate-state->clj
  "Returns the xstate state object as a clojure map."
  [state]
  (-> state
      (js/JSON.stringify)
      (js/JSON.parse)
      (js->clj :keywordize-keys true)
      (update :value keyword)
      (update :actions normalize-actions)))

(defn get-fsm-state-map
  "Returns the current state as a clojure map from db."
  [db [_ fsm-id]]
  (-> db
      (get-fsm-state [_ fsm-id])
      (xstate-state->clj)))

(rf/reg-sub
  ::fsm-state-map
  get-fsm-state-map)


(defn get-fsm-config
  "Returns the config of the FSM from db."
  [db [_ fsm-id]]
  (-> db
      (get-fsm [_ fsm-id])
      :config))

(rf/reg-sub
  ::fsm-config
  get-fsm-config)


(defn get-fsm-actor
  "Returns the xstate actor of the FSM from db."
  [db [_ fsm-id]]
  (-> db
      (get-fsm [_ fsm-id])
      :actor))

(rf/reg-sub
  ::fsm-actor
  get-fsm-actor)

(defn get-fsm-machine
  "Returns the xstate machine of the FSM from db."
  [db [_ fsm-id]]
  (-> db
      (get-fsm [_ fsm-id])
      :machine))

(rf/reg-sub
  ::fsm-machine
  get-fsm-machine)

(defn initialize-fsm
  "Initializes the FSM and updates the db with the FSM map."
  [{:keys [db]} [{:keys [id] :as fsm-config} options]]
  (let [machine (xs/createMachine (-> fsm-config
                                      (assoc :predictableActionArguments true)
                                      (clj->js))
                                  (-> options
                                      (clj->js)))
        actor (xs/interpret machine)
        fsm {:machine machine
             :actor   actor
             :config  fsm-config}]
    {:db (assoc-in db [::fsm id] fsm)
     :fx [[::actor-subscribe! fsm]
          [::actor-start! fsm]]}))

(rf/reg-event-fx
  ::initialize-fsm
  [rf/trim-v]
  initialize-fsm)

(defn update-state
  "Updates the state of the FSM in the db.
   Dispatches action events on transition if any."
  [{:keys [db]} [fsm-id state]]
  {:db (-> db
           (assoc-in [::fsm fsm-id :state] state))})

(rf/reg-event-fx
  ::update-state
  [rf/trim-v]
  update-state)

(defn on-state
  "Xstate actor subscribe listener, dispatches `::update-state` event"
  [fsm-id]
  (fn [new-state]
    (rf/dispatch [::update-state fsm-id new-state])))

(defn actor-subscribe!
  "Adds a listener to the actor to listen to state changes."
  [{:keys        [actor]
    {:keys [id]} :config}]
  (doto actor
    (.subscribe (on-state id))))

(rf/reg-fx
  ::actor-subscribe!
  actor-subscribe!)

(defn actor-start!
  "Starts xstate actor."
  [{:keys [actor]}]
  (doto actor
    (.start)))

(rf/reg-fx
  ::actor-start!
  actor-start!)

(defn actor-send!
  "Sends message to xstate actor"
  [[actor message]]
  (-> actor
      (.send (clj->js message))))

(rf/reg-fx
  ::actor-send!
  actor-send!)

(defn send
  "Dispatches an effect to send a message to an xstate actor"
  [{:keys [db]} [fsm-id message]]
  (let [actor (get-in db [::fsm fsm-id :actor])]
    {:fx [[::actor-send! [actor message]]]}))

(rf/reg-event-fx
  ::send
  [rf/trim-v]
  send)

(defn transition
  "Returns the new state given app-db, fsm-id, current state, and message"
  [db [_ fsm-id state message]]
  (-> db
      (get-fsm-machine [nil fsm-id])
      (.transition state message)))

(rf/reg-sub
  ::transition
  transition)


