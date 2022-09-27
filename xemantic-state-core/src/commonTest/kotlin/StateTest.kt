/*
 * xemantic-state - a Kotlin library providing hierarchical object state as reactive Flow of events
 * Copyright (C) 2022 Kazimierz Pogoda
 *
 * This file is part of xemantic-state.
 *
 * xemantic-state is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * xemantic-state is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with xemantic-state.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.xemantic.state

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class StateTest {

  // example state entity shared in tests
  class SoundControls(state: State.Builder) {
    var volume by state.property(100.0)
    inner class FrequencyFilter(state: State.Builder) {
      var low by state.property(20.0)
      var high by state.property(20000.0)
    }
    val frequencyFilter by state.child { FrequencyFilter(it) }
  }

  @Test
  fun shouldEmitChangesWhenStateEntityPropertiesAreChanged(): TestResult {
    // given
    val soundState = State { SoundControls(it) }
    val sound = soundState.entity
    val valueChanges = mutableListOf<State.Change<*>>()

    // when
    return runTest {
      val job = launch {
        soundState.changeFlow.collect {
          valueChanges.add(it)
        }
      }
      delay(1)
      sound.volume = 50.0
      delay(1)
      sound.frequencyFilter.high = 4200.0
      delay(1)
      job.cancelAndJoin()

      // then
      valueChanges shouldBe listOf(
        State.Change(
          path = listOf("volume"),
          source = soundState,
          current = 50.0,
          previous = 100.0
        ),
        State.Change(
          path = listOf("frequencyFilter", "high"),
          source = soundState,
          current = 4200.0,
          previous = 20000.0
        )
      )
    }
  }

  @Test
  fun shouldCollectAllTheChangesWhenBigAmountOfStateEntityPropertiesAreMutated(): TestResult {
    // given
    val repeatCount = 10000
    class CycleCounter(state: State.Builder) {
      var cycle: Int by state.property(0)
    }
    val cycleState = State { CycleCounter(it) }

    // when
    return runTest {
      val valueChanges = mutableListOf<State.Change<*>>()
      val job = launch {
        cycleState.changeFlow.collect {
          valueChanges.add(it)
        }
      }
      delay(1)
      repeat(repeatCount) {
        cycleState.entity.cycle = it + 1
        delay(1)
      }
      delay(1)
      job.cancelAndJoin()

      // then
      cycleState.entity.cycle shouldBe repeatCount
      valueChanges shouldHaveSize repeatCount
      valueChanges[0] shouldBe State.Change(
        path = listOf("cycle"),
        source = cycleState,
        current = 1,
        previous = 0
      )
      valueChanges[valueChanges.size - 1] shouldBe State.Change(
        path = listOf("cycle"),
        source = cycleState,
        current = repeatCount,
        previous = repeatCount - 1
      )
    }
  }

  @Test
  fun shouldDoubleBindDistributedStateOfARobotAndItsRemoteControl(): TestResult {
    // given
    class RobotControls(state: State.Builder) {
      var motorLeft: Double by state.property(0.0)
      var motorRight: Double by state.property(0.0)
    }

    val localState = State { RobotControls(it) }
    val remoteState = State { RobotControls(it) }

    // when
    return runTest {

      val localJob = launch {
        localState.changeFlow.collect {
          remoteState.update(it)
        }
      }
      val remoteJob = launch {
        remoteState.changeFlow.collect {
          localState.update(it)
        }
      }

      delay(1)

      val localControls = localState.entity
      val remoteControls = remoteState.entity
      localControls.motorLeft = 42.0
      remoteControls.motorRight = 84.0

      delay(1)

      localJob.cancelAndJoin()
      remoteJob.cancelAndJoin()

      // then
      localControls.motorLeft shouldBe 42.0
      localControls.motorRight shouldBe 84.0
      remoteControls.motorLeft shouldBe 42.0
      remoteControls.motorRight shouldBe 84.0
    }
  }

  @Test
  fun shouldEmitCurrentStateAsChanges(): TestResult {
    // given
    val soundState = State { SoundControls(it) }
    val valueChanges = mutableListOf<State.Change<*>>()

    // when
    return runTest {
      val job = launch {
        soundState.changeFlow.collect {
          valueChanges.add(it)
        }
      }
      delay(1)
      soundState.emit()
      delay(1)
      job.cancelAndJoin()

      // then
      valueChanges shouldBe listOf(
        State.Change(
          path = listOf("volume"),
          source = soundState,
          current = 100.0,
          previous = 100.0
        ),
        State.Change(
          path = listOf("frequencyFilter", "low"),
          source = soundState,
          current = 20.0,
          previous = 20.0
        ),
        State.Change(
          path = listOf("frequencyFilter", "high"),
          source = soundState,
          current = 20000.0,
          previous = 20000.0
        )
      )
    }
  }

  @Test
  fun shouldVisitAllThePropertiesInTheOrderTheyWereDefined() {
    // given
    val state = State { SoundControls(it) }
    val collector = mutableListOf<String>()

    // when
    state.visitProperties {
      collector.add(it.path.joinToString("."))
    }
    collector shouldBe listOf(
      "volume",
      "frequencyFilter",
      "frequencyFilter.low",
      "frequencyFilter.high"
    )
  }

  @Test
  fun shouldFindAllNestedProperties() {
    // given
    val state = State { SoundControls(it) }

    // when
    state.findProperty<Double>("volume").path shouldBe listOf("volume")
    state.findProperty<SoundControls.FrequencyFilter>("frequencyFilter").path shouldBe listOf("frequencyFilter")
    state.findProperty<Double>("frequencyFilter", "low").path shouldBe listOf("frequencyFilter", "low")
    state.findProperty<Double>("frequencyFilter", "high").path shouldBe listOf("frequencyFilter", "high")
  }

  @Test
  fun shouldThrowExceptionWhenSearchingForPropertyWithEmptyPath() {
    // given
    val state = State { SoundControls(it) }

    // when
    shouldThrowWithMessage<IllegalArgumentException>("Cannot find property with empty path") {
      state.findProperty<Any>()
    }
  }

  @Test
  fun shouldThrowExceptionWhenSearchingForNonExistentProperty() {
    // given
    val state = State { SoundControls(it) }

    // when
    shouldThrowWithMessage<IllegalArgumentException>("No such property, path: [foo]") {
      state.findProperty<Any>("foo")
    }
  }

  @Test
  fun shouldAssignPropertiesWithMetadata() {
    // given
    class RobotControls(state: State.Builder) {
      var speed: Double by state.property(0.0, 0.0..100.0)
      var heading: Double by state.property(0.0, 0.0..360.0)
    }

    val state = State { RobotControls(it) }
    val speedProperty = state.findProperty<Double>("speed")
    val headingProperty = state.findProperty<Double>("heading")

    // then
    speedProperty.metadata shouldBe 0.0..100.0
    headingProperty.metadata shouldBe 0.0..360.0
  }

  @Test
  fun shouldEmitOnlyOneStateChangeIfPropertyValueIsChangedMultipleTimesWithTheSameValue(): TestResult {
    // given
    val soundState = State { SoundControls(it) }
    val sound = soundState.entity
    val valueChanges = mutableListOf<State.Change<*>>()

    // when
    return runTest {
      val job = launch {
        soundState.changeFlow.collect {
          valueChanges.add(it)
        }
      }
      delay(1)
      sound.volume = 50.0
      delay(1)
      sound.volume = 50.0
      delay(1)
      job.cancelAndJoin()

      // then
      valueChanges shouldBe listOf(
        State.Change(
          path = listOf("volume"),
          source = soundState,
          current = 50.0,
          previous = 100.0
        )
      )
    }
  }

  @Test
  fun shouldAllowNullableProperty(): TestResult {
    // given
    class Agent(state: State.Builder) {
      var name: String? by state.property(null)
    }
    val agentState = State { Agent(it) }
    val agent = agentState.entity
    val valueChanges = mutableListOf<State.Change<*>>()

    // when
    return runTest {
      val job = launch {
        agentState.changeFlow.collect {
          valueChanges.add(it)
        }
      }
      delay(1)
      agent.name = "Foo"
      delay(1)
      agent.name = null
      delay(1)
      job.cancelAndJoin()

      // then
      valueChanges shouldBe listOf(
        State.Change(
          path = listOf("name"),
          source = agentState,
          current = "Foo",
          previous = null
        ),
        State.Change(
          path = listOf("name"),
          source = agentState,
          current = null,
          previous = "Foo"
        )
      )
    }
  }

}
