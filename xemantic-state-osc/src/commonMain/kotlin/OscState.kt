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

interface OscState : AutoCloseable {

  class Builder {
    var ip: String? = null
    var address: String? = null
    var port: Int = 42001
  }

  fun <T> exportState(
    address: String,
    emitAllProperty: String = "emitAll",
    state: State<T>
  )

  fun output(builder: OscState.Output.Builder.() -> Unit): OscState.Output

  interface Output : AutoCloseable {

    class Builder {
      var localPort: Int = 42002
      var ip: String? = null
      var port: Int = 42003
    }

    fun <T> send(address: String, value: T)

    fun emitState(address: String, remoteAddress: String?)

  }

}

interface Converter<T> {
  fun toOsc(value: T): List<*>
  fun fromOsc(args: List<*>): T
}

class DoubleConverter : Converter<Double> {
  override fun toOsc(value: Double) = listOf(value.toFloat())
  override fun fromOsc(args: List<*>) = (args.first() as Float).toDouble()
}

class IntConverter : Converter<Int> {
  override fun toOsc(value: Int) = listOf(value)
  override fun fromOsc(args: List<*>) = (args[0] as Int)
}

class BooleanConverter : Converter<Boolean> {
  override fun toOsc(value: Boolean) = listOf(if (value) 1 else 0)
  override fun fromOsc(args: List<*>) = ((args[0] as Int) == 1)
}
