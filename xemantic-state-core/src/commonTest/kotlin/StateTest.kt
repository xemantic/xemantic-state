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

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class StateTest {

  @Test
  fun shouldEmitStateChanges(): TestResult {
    class RobotControls(state: StateBuilder) {
      var motor: Double by state.property(0.0)
    }
    val robotState = state { RobotControls(it) }
    val robotControls = robotState.entity

    return runTest {
      val valueChanges = mutableListOf<ValueChange<*>>()
      val job = launch {
        robotState.valueChangeFlow.collect {
          valueChanges.add(it)
        }
      }
      delay(1)
      robotControls.motor = 42.0
      delay(1)
      job.cancelAndJoin()
      valueChanges shouldHaveSize 1
      valueChanges shouldContain ValueChange(
        path = listOf("motor"),
        source = robotState,
        current = 42.0,
        previous = 0.0
      )
    }
  }

  @Test
  fun shouldEmitMassiveAmountOfChanges(): TestResult {
    val repeatCount = 10000
    class CycleCounter(state: StateBuilder) {
      var cycle: Int by state.property(0)
    }
    val cycleState = state { CycleCounter(it) }

    return runTest {
      val valueChanges = mutableListOf<ValueChange<*>>()
      val job = launch {
        cycleState.valueChangeFlow.collect {
          valueChanges.add(it)
        }
      }
      delay(1000)
      repeat(repeatCount) {
        cycleState.entity.cycle = it + 1
        delay(1)
      }
      delay(1000)
      job.cancelAndJoin()
      cycleState.entity.cycle shouldBe repeatCount
      valueChanges shouldHaveSize repeatCount
      valueChanges[0] shouldBe ValueChange(
        path = listOf("cycle"),
        source = cycleState,
        current = 1,
        previous = 0
      )
      valueChanges[valueChanges.size - 1] shouldBe ValueChange(
        path = listOf("cycle"),
        source = cycleState,
        current = repeatCount,
        previous = repeatCount - 1
      )
    }
  }

  @Test
  fun shouldTrackChangesOfHierarchicalState(): TestResult {
    // given
    class Controls(state: StateBuilder) {
      var volume by state.property(100.0)
      var frequency by state.property(440.0)
      inner class Fade(state: StateBuilder) {
        var factor by state.property(1.0)
      }
      val fade by state.child { Fade(it) }
    }

    val state = state { Controls(it) }
    val controls = state.entity

    return runTest {
      val job = launch {
        state.valueChangeFlow.collect {
          println(it)
        }
      }
      delay(1)
      controls.volume = 10.0
      controls.frequency = 880.0
      controls.fade.factor = 42.0

      delay(1)

      job.cancelAndJoin()

    }
  }

  @Test
  fun shouldDoubleBoundDistributedStateOfARobotAndItsRemoteControl(): TestResult {
    // given
    class RobotControls(state: StateBuilder) {
      var motorLeft: Double by state.property(0.0)
      var motorRight: Double by state.property(0.0)
    }

    val localState = state { RobotControls(it) }
    val remoteState = state { RobotControls(it) }

    // when
    return runTest {

      val localJob = launch {
        localState.valueChangeFlow.collect { remoteState.update(it) }
      }
      val remoteJob = launch {
        remoteState.valueChangeFlow.collect { localState.update(it) }
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

}
