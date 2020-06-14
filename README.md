# xemantic-state
Small kotlin library for transforming state beans into reactive event streams.

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
 