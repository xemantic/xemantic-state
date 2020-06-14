# xemantic-state
Small kotlin library for transforming state beans into reactive event streams. Even though small,
it has multitude of use cases in my projects.

# Use cases

## Reacting to state mutations

### In UI interfaces

```kotlin
flowPanel {
  button("Dock") {
    actionEvents.subscribe {
      state.robotMode = RobotMode.DOCKING
    }
    events.robotMode.subscribe { mode ->
      isEnabled = mode == RobotMode.FREE
    }
  }
}
```

See [xemantic-kotlin-swing-dsl](https://github.com/xemantic/xemantic-kotlin-swing-dsl) project
for more details
 
### In robotics

Collected sensor data is often a static state transmitted through the serial interface and
obtained in a busy loop. But with `xemantic-state` it can be easily upgraded to reactive event
streams delivering changes as they happen and triggering responses. It allows to avoid complexity
and conditional execution paths. Also certain classes of problems are easier to solve - especially
feedback loop based reactions. It's much easier to model certain aspects of cybernetic system
with [Functional Reactive Programming](https://en.wikipedia.org/wiki/Functional_reactive_programming)
in mind.

```kotlin
events.robotMode
    .filter { mode -> mode == RobotMode.DOCKING }
    .doOnNext {
      logger.info { "Docking" }
      roomba.seekDock()
    }
    .flatMapMaybe {
      Observables.combineLatest(events.oiMode, events.current)
          .filter { pair ->
            (pair.first == OiMode.PASSIVE) && (pair.second > 0)
          }
          .firstElement()
    }
    .subscribe {
      state.robotMode = RobotMode.DOCKED
      logger.info { "Docked" }
    }
```

Once robot mode changes to `DOCKING`, command the Roomba robotic vacuum cleaner to seek the dock
for charging. Roomba is not informing if the actual docking happens, therefore we wait until both -
OI mode is switching to `PASSIVE` and the electric current is positive.

Imagine trying to model this as conditions in the busy loop continuously reading robot state. 

## 2-way synchronization of state objects

### distributed state

### transparent use of OSC protocol

### remoting of MIDI control and having 

# TODO

## xemantic-state-generator module

An apt processor generating classes like:

```kotlin
class MyStateEvents(e: Events<TestStateEvents>) {
  val foo by e.events<Int>()
  val bar by e.events<String>()
}
```

for corresponding:

```kotlin
class MyState(s: State<TestState>) {
  var foo by s.mutableProperty(42)
  var bar by s.mutableProperty("baz")
}
```

## validation

Classes passed to the `Keeper` instance, even if generated, should be
verified for correctness.
 