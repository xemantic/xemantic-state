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

import com.xemantic.state.State
import com.xemantic.state.state
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class JvmOscStateTest {

  // classes shared in tests
  class SoundControls(state: State.Builder) {
    var volume by state.property(100.0)
    inner class FrequencyFilter(state: State.Builder) {
      var low by state.property(20.0)
      var high by state.property(20000.0)
    }
    val frequencyFilter by state.child { FrequencyFilter(it) }
  }

  @Test
  fun shouldExposeLocalStateAsOscAddresses() {
    // given
    val state = state { SoundControls(it) }
    val sound = state.entity
    oscState(
      port = 12345,
      state = state,
      converters = defaultConverters
    ).use { oscState ->
      oscState.exportState(
        address = "/sound",
        emitAllTriggerAddress = "soundEmitAll",
        state = state
      )
      oscOut(12346, 12345) {
        send("/sound/volume", 42.0)
        send("/sound/frequencyFilter/low", 4242.0)
        send("/sound/frequencyFilter/high", 424242.0)
      }
      Thread.sleep(1)
      sound.volume shouldBe 42.0
      sound.frequencyFilter.low shouldBe 4242.0
      sound.frequencyFilter.high shouldBe 424242.0
    }
  }

}
