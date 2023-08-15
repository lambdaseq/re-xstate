# re-xstate

`re-xstate` is a thin re-frame wrapper over XState. It provides the power of XState state machines and statecharts to
re-frame applications. Unlike directly using XState, `re-xstate` doesn't use XState for context management. Instead, it
manages context on its own to work seamlessly with EDN data. This approach avoids the need to convert context from EDN
to JSON and vice versa, which can lead to loss of information.

## Installation

To include `re-xstate` in your project, add the following to your `deps.edn`:

```clojure
{:deps
 {io.github.drbuchkov/re-xstate {:mvn/version "0.0.9"}}}
```

Replace `YOUR_COMMIT_SHA` with the commit SHA you want to use.

## Key Features

- **State Management**: Define and manage application state using state machines and statecharts.
- **EDN Compatibility**: Works seamlessly with EDN data without the need for JSON conversion.
- **Re-frame Integration**: Provides re-frame events and subscriptions for state management.

## Usage

### Events:

- `::rxs/initialize-fsm`: Initializes a finite state machine.
- `::rxs/send`: Sends an event to a state machine.
- `::rxs/stop`: Stops a state machine.
- `::rxs/reset`: Resets a state machine to its initial state.
- `::rxs/assign`: Assigns new context to a state machine.
- `::rxs/update-context`: Given a `ctx-action` (created ) function and an event, updates the context of the FSM in the
  db.
  This is used instead of the default xstate context updating, because the default xstate context updating
  doesn't work with re-frame subscriptions, and converting from edn to json and back results in a loss of data.

### Subscriptions:

- `::rxs/fsm`: Retrieves the entire state machine.
- `::rxs/fsm-state`: Retrieves the current state of a state machine.
- `::rxs/fsm-state-value`: Retrieves the current state value of a state machine.
- `::rxs/fsm-context`: Retrieves the context of a state machine.
- `::rxs/fsm-state-map`: Retrieves the current state value and context of a state machine.

### Core Functions:

- `wrap-ctx-action`: Given a context action function, returns a 2-arity callback that can be used as an xstate action.
  The event is converted to edn before being passed to the action function.
- `wrap-effectful-action`: Given an effectful action function, returns a 2-arity callback that can be used as an
  xstate action. The event is converted to edn before being passed to the action function.
- `wrap-guard`: Given a guard function, returns a 2-arity callback that can be used as an xstate guard. The event is
  converted to edn before being passed to the guard function.

## Examples

### Toggle Example

Here's a simple example from the `/examples` folder showcasing a toggle functionality:

**Namespace and Requirements:**

```clojure
(ns toggle.views
  (:require
   [drbuchkov.re-xstate.core :as rxs]
   [re-frame.core :as rf]))
```

**Machine Configuration:**

```clojure
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
```

**Machine Initialization:**

```clojure
(rf/dispatch [::rxs/initialize-fsm toggle-state-machine-config toggle-state-machine-options])
```

**Usage:**

```clojure
(defn toggle-view []
  (let [state (rf/subscribe [::rxs/fsm-state :toggle])]
    [:div
     [:h1 (str "Toggle is " @state)]
     [:button {:on-click #(rf/dispatch [::rxs/send :toggle {:type :TOGGLE}])}
      "Toggle"]]))
```

For more examples and detailed usage, refer to the provided example namespaces:

- [Toggle Views](https://raw.githubusercontent.com/DrBuchkov/re-xstate/main/examples/toggle/src/toggle/views.cljs)
- [Toggle Main](https://raw.githubusercontent.com/DrBuchkov/re-xstate/main/examples/toggle/src/toggle/main.cljs)