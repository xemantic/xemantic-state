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
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest

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

  private lateinit var oscState: OscState

  private var oscSender: OscSender? = null

  @BeforeTest
  fun setUp() {
    oscState = oscState {
      port = 42001
    }
  }

  @AfterTest
  fun tearDown() {
    if (oscSender != null) {
      oscSender!!.close()
    }
    oscState.close()
  }

  /*
  @Test
  fun shouldCreateOscState() {
    // given
    val soundState = state { SoundControls(it) }
    val sound = soundState.entity
    oscSender = oscSender {
      port = 42001
    }
    oscState.export("/sound", soundState)

    // when
    oscSender!!.send("/sound/volume", 50.0)

    GlobalScope.launch {
      delay(10000)
    }

    //sound.volume shouldBe 50.0
  }
  */


}
