(ns drbuchkov.re-xstate.core
  (:require [applied-science.js-interop :as j]
            [re-frame.core :as rf]
            [potpuri.core :as pt]
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

(defn get-fsm-context
  "Returns the context of the FSM from db."
  [db [_ fsm-id]]
  (-> db
      (get-fsm [_ fsm-id])
      :context))

(rf/reg-sub
  ::fsm-context
  get-fsm-context)

(defn normalize-actions
  "Normalizes the actions by converting the type to a keyword."
  [actions]
  (->> actions
       (map #(-> %
                 (update :type keyword)))))

(defn xstate-state->clj
  "Returns the xstate state object as a clojure map."
  [state]
  (let [context (j/get state :context)
        state (j/assoc! state :context nil)]
    (some-> state
            (js/JSON.stringify)
            (js/JSON.parse)
            (js->clj :keywordize-keys true)
            (update :value keyword)
            (update :actions normalize-actions)
            (assoc :context context))))

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

(defn evt->edn
  "Converts the xstate event object to edn."
  [evt]
  (-> evt
      (js->clj :keywordize-keys true)
      (update :type keyword)))

(defn- -preprocess-fsm-config [fsm-config]
  (-> fsm-config
      (assoc :predictableActionArguments true)
      (dissoc :context)
      (clj->js)))

(defn ctx-action
  "Given a context action function, returns a 2-arity callback that can be used as an xstate action.
   The event is converted to edn before being passed to the action function."
  [action-fn]
  (fn [_ evt]
    (rf/dispatch [::update-context (evt->edn evt) action-fn])))

(defn effectful-action
  "Given an effectful action function, returns a 2-arity callback that can be used as an xstate action.
   The event is converted to edn before being passed to the action function."
  [action-fn]
  (fn [_ evt]
    (rf/dispatch [::run-effectful-action (evt->edn evt) action-fn])))

(defn wrap-guard
  "Given a function that takes an event and returns a boolean, returns a 2-arity callback that can be used as an xstate guard.
   The event is converted to edn before being passed to the guard function."
  [guard-fn]
  (fn [_ evt]
    (let [evt (evt->edn evt)]
      (guard-fn evt))))

(defn- -preprocess-fsm-options [fsm-options]
  (-> fsm-options
      (clj->js)))

(defn initialize-fsm
  "Initializes the FSM and updates the db with the FSM map."
  [{:keys [db]} [{:keys [id context] :as fsm-config} fsm-options]]
  (let [machine (xs/createMachine (-> fsm-config
                                      (-preprocess-fsm-config))
                                  (-> fsm-options
                                      (-preprocess-fsm-options)))
        actor (xs/interpret machine)
        fsm {:machine machine
             :actor   actor
             :config  fsm-config
             :context context}
        existing-actor (get-fsm-actor db [nil id])]
    {:db (assoc-in db [::fsm id] fsm)
     :fx [[::actor-stop! existing-actor]
          [::actor-subscribe! fsm]
          [::actor-start! fsm]]}))

(rf/reg-event-fx
  ::initialize-fsm
  [rf/trim-v]
  initialize-fsm)

(defn update-state
  "Updates the state of the FSM in the db.
   Dispatches action events on transition if any."
  [{:keys [db]} [fsm-id state]]
  {:db (-> db (assoc-in [::fsm fsm-id :state] state))})

(rf/reg-event-fx
  ::update-state
  [rf/trim-v]
  update-state)

(defn update-context
  "Given a ctx-action function and an event, updates the context of the FSM in the db.
   This is used instead of the default xstate context updating, because the default xstate context updating
   doesn't work with re-frame subscriptions, and converting from edn to json and back results in a loss of data."
  [{:keys [db]} [{:keys [fsm-id] :as evt} action-fn]]
  (let [new-ctx (action-fn evt)]
    {:db (-> db
             (assoc-in [::fsm fsm-id :context] new-ctx))}))
(rf/reg-event-fx
  ::update-context
  [rf/trim-v]
  update-context)

(defn run-effectful-action!
  "Runs the effectful action function, given the event."
  [[effect evt]]
  (effect evt))

(rf/reg-fx
  ::run-effectful-action!
  run-effectful-action!)

(rf/reg-event-fx
  ::run-effectful-action
  [rf/trim-v]
  (fn [_ [evt effect]]
    {:fx [[::run-effectful-action! [effect evt]]]}))

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

(defn actor-stop!
  "Stops xstate actor."
  [actor]
  (some-> actor
          (doto (.stop))))

(rf/reg-fx
  ::actor-stop!
  actor-stop!)

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
  "Dispatches an effect to send a message to a xstate actor.
  `fsm-id`, `transition-actions` and `ctx` are injected to the message."
  [{:keys [db]} [fsm-id {:keys [type] :as message}]]
  (let [{:keys [value]} (get-fsm-state-map db [nil fsm-id])
        machine-config (get-fsm-config db [nil fsm-id])
        context (get-fsm-context db [nil fsm-id])
        transition-actions (get-in machine-config [:states value :on type :actions])
        actor (get-fsm-actor db [nil fsm-id])]
    {:fx [[::actor-send! [actor (assoc message
                                  :fsm-id fsm-id
                                  :transition-actions (if (map? transition-actions)
                                                        [transition-actions]
                                                        transition-actions)
                                  :ctx context)]]]}))

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

(defn action-params
  "Returns the params for the given action-id in the transition-actions property that's injected from
   the `send` event into the message."
  [{:keys [transition-actions] :as _evt} action-id]
  (-> transition-actions
      (pt/find-first
        {:type (cond
                 (keyword? action-id) (name action-id)
                 :default action-id)})
      :params))


