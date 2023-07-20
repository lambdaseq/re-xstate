(ns drbuchkov.re-xstate.core
  (:require [re-frame.core :as rf]
            ["xstate" :as xs]))

(defn get-state-machine
  [db [_ state-machine-id]]
  (get-in db [::state-machines state-machine-id]))

(rf/reg-sub
  ::state-machine
  get-state-machine)

(defn get-state-machine-state [db [_ state-machine-id]]
  (-> db
      (get-state-machine [_ state-machine-id])
      :state))

(rf/reg-sub
  ::state-machine-state
  get-state-machine-state)


(defn get-state-machine-config [db [_ state-machine-id]]
  (-> db
      (get-state-machine [_ state-machine-id])
      :config))

(rf/reg-sub
  ::state-machine-configuration
  get-state-machine-config)


(defn get-state-machine-actor [db [_ state-machine-id]]
  (-> db
      (get-state-machine [_ state-machine-id])
      :actor))

(rf/reg-sub
  ::state-machine-actor
  get-state-machine-actor)

(defn get-state-machine-machine [db [_ state-machine-id]]
  (-> db
      (get-state-machine [_ state-machine-id])
      :machine))

(rf/reg-sub
  ::state-machine-machine
  get-state-machine-machine)

(defn initialize-state-machine [{:keys [db]} [{:keys [id] :as state-machine-config}]]
  (let [machine (xs/createMachine (clj->js state-machine-config))
        actor (xs/interpret machine)
        state-machine {:machine machine
                       :actor   actor
                       :config  state-machine-config}]
    {:db (assoc-in db [::state-machines id] state-machine)
     :fx [[::actor-subscribe! state-machine]
          [::actor-start! state-machine]]}))

(rf/reg-event-fx
  ::initialize-state-machine
  [rf/trim-v]
  initialize-state-machine)

(defn update-state [{:keys [db]} [state-machine-id state]]
  {:db (-> db
           (assoc-in [::state-machines state-machine-id :state] state))})

(rf/reg-event-fx
  ::update-state
  [rf/trim-v]
  update-state)

(defn on-state [state-machine-id]
  (fn [new-state]
    (js/console.log new-state)
    (let [new-state (-> new-state
                        (js/JSON.stringify)
                        (js/JSON.parse)
                        (js->clj :keywordize-keys true)
                        (update :value keyword))]
      (rf/dispatch [::update-state state-machine-id new-state]))))

(defn actor-subscribe!
  [{:keys        [actor]
    {:keys [id]} :config}]
  (doto actor
    (.subscribe (on-state id))))

(rf/reg-fx
  ::actor-subscribe!
  actor-subscribe!)

(defn actor-start!
  [{:keys [actor]}]
  (doto actor
    (.start)))

(rf/reg-fx
  ::actor-start!
  actor-start!)

(defn actor-send! [[actor message]]
  (-> actor
      (.send (clj->js message))))

(rf/reg-fx
  ::actor-send!
  actor-send!)

(defn send [{:keys [db]} [state-machine-id message]]
  (let [actor (get-in db [::state-machines state-machine-id :actor])]
    {:fx [[::actor-send! [actor message]]]}))

(rf/reg-event-fx
  ::send
  [rf/trim-v]
  send)

(defn transition [db [_ state-machine-id state message]]
  (-> db
      (get-state-machine-machine [nil state-machine-id])
      (.transition state message)))

(rf/reg-sub
  ::transition
  transition)


