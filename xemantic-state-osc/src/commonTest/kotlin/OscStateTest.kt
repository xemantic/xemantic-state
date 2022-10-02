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

package com.xemantic.state.osc

import com.xemantic.osc.osc
import com.xemantic.state.State
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

// example state entity shared in tests
class SoundControls(state: State.Builder) {
  var volume by state.property(100.0)
  inner class FrequencyFilter(state: State.Builder) {
    var low by state.property(20.0)
    var high by state.property(20000.0)
  }
  val frequencyFilter by state.child { FrequencyFilter(it) }
}

@ExperimentalCoroutinesApi
class OscStateTest {

  @Test
  fun shouldCreateOscState() {
    // given
    val soundState = State { SoundControls(it) }
    val statefulOsc = StatefulOsc {
      export("/sound", soundState)
    }
    val sound = soundState.entity
    return runTest {
      val job = launch {
        statefulOsc.osc.messageFlow.collect {
          delay(1)
          coroutineContext.job.cancel()
          delay(1)
        }
      }

      delay(1)
      osc {}.output {
        port = statefulOsc.osc.port
        conversion<Double>("/sound/volume")
      }.use { out ->
        out.send("/sound/volume", 50.0)
      }
      job.join()
      // TODO it should be fixed differently
      Thread.sleep(1000)
      sound.volume shouldBe 50.0
    }
  }

  @Test
  fun shouldSynchronizeOscStates() {
    // given
    val soundStateA = State { SoundControls(it) }
    val soundA = soundStateA.entity
    val soundStateB = State { SoundControls(it) }
    val soundB = soundStateB.entity
    val statefulOscA = StatefulOsc {
      export("/sound", soundStateA)
    }
    val statefulOscB = StatefulOsc {
      export("/sound", soundStateB)
    }
    statefulOscA.subscribeRemote(
      hostname = statefulOscB.osc.hostname,
      port = statefulOscB.osc.port,
      address = "/sound"
    )
    statefulOscB.subscribeRemote(
      hostname = statefulOscA.osc.hostname,
      port = statefulOscA.osc.port,
      address = "/sound"
    )

    return runTest {

      val jobA = launch {
        soundStateA.changeFlow.take(1).collect {}
      }
      val jobB = launch {
        soundStateB.changeFlow.take(1).collect {}
      }

      soundA.volume = 50.0
      soundB.frequencyFilter.low = 10.0

      jobB.join()
      jobA.join()

      soundB.volume shouldBe 50.0
      soundA.frequencyFilter.low shouldBe 10.0
    }
  }

}
