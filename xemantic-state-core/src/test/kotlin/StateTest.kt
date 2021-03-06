/*
 * xemantic-state - kotlin library for transforming state beans into
 * reactive event streams
 * Copyright (C) 2020  Kazimierz Pogoda
 *
 * This file is part of xemantic-state.
 *
 * xemantic-state  is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * xemantic-state is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with xemantic-state.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.xemantic.state.core

import ch.tutteli.atrium.api.fluent.en_GB.toBe
import ch.tutteli.atrium.api.verbs.expect
import io.reactivex.rxjava3.observers.TestObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.jupiter.api.Test

class StateTest {

  // given
  open class TestState(s: State<TestState>) {
    var foo by s.property(42)
    var bar by s.property("baz")
    open val qux by s.property("thud")
  }

  // and corresponding events object
  // Note: in the future it will be autogenerated
  class TestStateEvents(e: Events<TestStateEvents>) {
    val foo by e.events<Int>()
    val bar by e.events<String>()
    val qux by e.events<String>()
  }

  open class MutableQuxTestState(s: State<TestState>): TestState(s) {
    override var qux by s.property("thud")
  }

  @Test
  fun `should create StateKeeper instance`() {
    StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.single()
    )
  }

  @Test
  fun `should propagate default value on first subscription`() {
    // given
    val keeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    val events = keeper.events
    val foos = TestObserver<Int>()
    val bars = TestObserver<String>()
    val quxs = TestObserver<String>()

    // when
    events.foo.subscribe(foos)
    events.bar.subscribe(bars)
    events.qux.subscribe(quxs)

    // then
    foos.assertValue(42)
    bars.assertValue("baz")
    quxs.assertValues("thud")
  }

  @Test
  fun `should publish state mutations`() {
    // given
    val keeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    val state = keeper.state
    val events = keeper.events
    val foos = TestObserver<Int>()
    val bars = TestObserver<String>()
    val quxs = TestObserver<String>()
    val changes = TestObserver<StateChangeEvent<Any>>()
    events.foo.subscribe(foos)
    events.bar.subscribe(bars)
    events.qux.subscribe(quxs)
    keeper.stateChangeEvents.subscribe(changes)

    // when
    state.foo = 666
    state.bar = "satan"

    // then
    foos.assertValues(42, 666)
    bars.assertValues("baz", "satan")
    quxs.assertValues("thud")
    changes.assertValues(
        StateChangeEvent("foo", 666),
        StateChangeEvent("bar", "satan")
    )
  }

  @Test
  fun `onExternalChange() should not publish to stateChangeEvents`() {
    // given
    val keeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    val events = keeper.events
    val foos = TestObserver<Int>()
    val bars = TestObserver<String>()
    val quxs = TestObserver<String>()
    events.foo.subscribe(foos)
    events.bar.subscribe(bars)
    events.qux.subscribe(quxs)
    val changes = TestObserver<StateChangeEvent<Any>>()
    keeper.stateChangeEvents.subscribe(changes)

    // when
    keeper.externalChange("foo", 666)
    keeper.externalChange("bar", "satan")
    keeper.externalChange("qux", "demon")

    // then
    expect(keeper.state.foo).toBe(666)
    expect(keeper.state.bar).toBe("satan")
    expect(keeper.state.qux).toBe("demon")
    changes.assertEmpty()
    foos.assertValues(42, 666)
    bars.assertValues("baz", "satan")
    quxs.assertValues("thud", "demon")
  }

  @Test
  fun `publish() should generate state change events`() {
    // given
    val keeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    val events = keeper.events
    val foos = TestObserver<Int>()
    val bars = TestObserver<String>()
    val quxs = TestObserver<String>()
    events.foo.subscribe(foos)
    events.bar.subscribe(bars)
    events.qux.subscribe(quxs)
    val changes = TestObserver<StateChangeEvent<Any>>()
    keeper.stateChangeEvents.subscribe(changes)

    // when
    keeper.publish()

    // then
    changes.assertValues(
        StateChangeEvent("bar", "baz"),
        StateChangeEvent("foo", 42),
        StateChangeEvent("qux", "thud")
    )
  }

  @Test
  fun `properties() should return Observable of property names`() {
    // given
    val keeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )

    // when
    val properties = keeper.properties()

    // then
    expect(properties.toSortedList().blockingGet())
        .toBe(listOf("bar", "foo", "qux"))
  }

  // normally performed with some transport-mechanism, like OSC protocol
  @Test
  fun `use case - state publishing`() {
    // given
    val localKeeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    val remoteKeeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    localKeeper.stateChangeEvents.subscribe { event ->
      remoteKeeper.externalChange(event.property, event.value)
    }
    val localFoos = TestObserver<Int>()
    val localBars = TestObserver<String>()
    val localQuxs = TestObserver<String>()
    val localChanges = TestObserver<StateChangeEvent<Any>>()
    val remoteFoos = TestObserver<Int>()
    val remoteBars = TestObserver<String>()
    val remoteQuxs = TestObserver<String>()
    val remoteChanges = TestObserver<StateChangeEvent<Any>>()
    localKeeper.events.foo.subscribe(localFoos)
    localKeeper.events.bar.subscribe(localBars)
    localKeeper.events.qux.subscribe(localQuxs)
    localKeeper.stateChangeEvents.subscribe(localChanges)
    remoteKeeper.events.foo.subscribe(remoteFoos)
    remoteKeeper.events.bar.subscribe(remoteBars)
    remoteKeeper.events.qux.subscribe(remoteQuxs)
    remoteKeeper.stateChangeEvents.subscribe(remoteChanges)
    remoteKeeper.state.foo = 0
    remoteKeeper.state.bar = "somethingElse"

    // when
    localKeeper.publish()

    // then
    expect(localKeeper.state.foo).toBe(42)
    expect(localKeeper.state.bar).toBe("baz")
    expect(localKeeper.state.qux).toBe("thud")
    expect(remoteKeeper.state.foo).toBe(42)
    expect(remoteKeeper.state.bar).toBe("baz")
    expect(remoteKeeper.state.qux).toBe("thud")
    localFoos.assertValues(42)
    localBars.assertValues("baz")
    localQuxs.assertValues("thud")
    remoteFoos.assertValues(42, 0, 42)
    remoteBars.assertValues("baz", "somethingElse", "baz")
    remoteQuxs.assertValues("thud")
    localChanges.assertValues(
        StateChangeEvent("bar", "baz"),
        StateChangeEvent("foo", 42),
        StateChangeEvent("qux", "thud")
    )
    remoteChanges.assertValues(
        StateChangeEvent("foo", 0),
        StateChangeEvent("bar", "somethingElse")
    )
  }

  // normally performed with some transport-mechanism, like OSC protocol
  @Test
  fun `use case - state synchronization`() {
    // given
    val localKeeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    val remoteKeeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    localKeeper.stateChangeEvents.subscribe { event ->
      remoteKeeper.externalChange(event.property, event.value)
    }
    remoteKeeper.stateChangeEvents.subscribe { event ->
      localKeeper.externalChange(event.property, event.value)
    }
    val localFoos = TestObserver<Int>()
    val localBars = TestObserver<String>()
    val localQuxs = TestObserver<String>()
    val localChanges = TestObserver<StateChangeEvent<Any>>()
    val remoteFoos = TestObserver<Int>()
    val remoteBars = TestObserver<String>()
    val remoteQuxs = TestObserver<String>()
    val remoteChanges = TestObserver<StateChangeEvent<Any>>()
    localKeeper.events.foo.subscribe(localFoos)
    localKeeper.events.bar.subscribe(localBars)
    localKeeper.events.qux.subscribe(localQuxs)
    localKeeper.stateChangeEvents.subscribe(localChanges)
    remoteKeeper.events.foo.subscribe(remoteFoos)
    remoteKeeper.events.bar.subscribe(remoteBars)
    remoteKeeper.events.qux.subscribe(remoteQuxs)
    remoteKeeper.stateChangeEvents.subscribe(remoteChanges)

    // when
    localKeeper.state.foo += 100
    localKeeper.state.bar += "Local"
    remoteKeeper.state.foo += 1000
    remoteKeeper.state.bar += "Remote"

    // then
    expect(localKeeper.state.foo).toBe(1142)
    expect(localKeeper.state.bar).toBe("bazLocalRemote")
    expect(localKeeper.state.qux).toBe("thud")
    expect(remoteKeeper.state.foo).toBe(1142)
    expect(remoteKeeper.state.bar).toBe("bazLocalRemote")
    expect(remoteKeeper.state.qux).toBe("thud")
    localFoos.assertValues(42, 142, 1142)
    localBars.assertValues("baz", "bazLocal", "bazLocalRemote")
    localQuxs.assertValues("thud")
    remoteFoos.assertValues(42, 142, 1142)
    remoteBars.assertValues("baz", "bazLocal", "bazLocalRemote")
    remoteQuxs.assertValues("thud")
    localChanges.assertValues(
        StateChangeEvent("foo", 142),
        StateChangeEvent("bar", "bazLocal")
    )
    remoteChanges.assertValues(
        StateChangeEvent("foo", 1142),
        StateChangeEvent("bar", "bazLocalRemote")
    )
  }

  @Test
  fun `use case - read-only state synchronization`() {
    // given
    val localKeeper = StateKeeper(
        MutableQuxTestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    val remoteKeeper = StateKeeper(
        TestState::class,
        TestStateEvents::class,
        Schedulers.trampoline()
    )
    localKeeper.stateChangeEvents.subscribe { event ->
      remoteKeeper.externalChange(event.property, event.value)
    }
    remoteKeeper.stateChangeEvents.subscribe { event ->
      localKeeper.externalChange(event.property, event.value)
    }
    val localQuxs = TestObserver<String>()
    val localChanges = TestObserver<StateChangeEvent<Any>>()
    val remoteQuxs = TestObserver<String>()
    val remoteChanges = TestObserver<StateChangeEvent<Any>>()
    localKeeper.events.qux.subscribe(localQuxs)
    localKeeper.stateChangeEvents.subscribe(localChanges)
    remoteKeeper.events.qux.subscribe(remoteQuxs)
    remoteKeeper.stateChangeEvents.subscribe(remoteChanges)

    // when
    localKeeper.state.qux += "Local"

    // then
    expect(localKeeper.state.qux).toBe("thudLocal")
    expect(remoteKeeper.state.qux).toBe("thudLocal")
    localQuxs.assertValues("thud", "thudLocal")
    remoteQuxs.assertValues("thud", "thudLocal")
    localChanges.assertValues(
        StateChangeEvent("qux", "thudLocal")
    )
    remoteChanges.assertEmpty()
  }

}
