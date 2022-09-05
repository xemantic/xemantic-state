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
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface OscState {

  class Builder {
    var ip: String? = null
    var port: Int = 42001
    var converters: Map<KType, OscPropertyConverter<*, *>> = defaultOscPropertyConverters
  }

  fun <T> export(
    address: String,
    state: State<T>,
    emitAllProperty: String = "emitAll"
  )

  fun output(builder: Output.Builder.() -> Unit): Output

  fun close()

  interface Output {

    class Builder {
      var ip: String? = null
      var port: Int = 42003
    }

    fun emitOnChanges(address: String, remoteAddress: String? = address)

    fun close()

  }

}

expect fun oscState(builder: OscState.Builder.() -> Unit): OscState

interface OscSender {

  class Builder {
    var ip: String? = null
    var port: Int = 42001
    var converters: Map<KType, OscPropertyConverter<*, *>> = defaultOscPropertyConverters
  }

  fun <T> send(address: String, value: T)

  fun close()

}

// Useful in tests
expect fun oscSender(builder: OscSender.Builder.() -> Unit): OscSender

// This should be completely changed, and handled by external xemantic-osc, providing converters + OSC type indicator, to support also null
class OscPropertyConverter<V, O>(
  val encode: (x: V) -> O,
  val decode: (x: O) -> V
)

fun oscPropertyConverters(
  block: OscPropertyConvertersBuilder.() -> Unit
): Map<KType, OscPropertyConverter<*, *>> {
  val builder = OscPropertyConvertersBuilder()
  block(builder)
  return builder.converters.toMap()
}

class OscPropertyConvertersBuilder {

  val converters =
    mutableListOf<Pair<KType, OscPropertyConverter<*, *>>>()

  inline fun <reified V, O> convert(
    noinline encode: (x: V) -> O,
    noinline decode: (x: O) -> V
  ) {
    converters.add(
      Pair(
        typeOf<V>(),
        OscPropertyConverter(encode, decode)
      )
    )
  }

}

val defaultOscPropertyConverters = oscPropertyConverters {
  convert<Float, Float>(
    { v -> v },
    { o -> o }
  )
  convert<Boolean, Boolean>(
    { v -> v },
    { o -> o }
  )
  convert<Double, Float>(
    { v -> v.toFloat() },
    { o -> o.toDouble() }
  )
  convert<Int, Int>(
    { v -> v },
    { o -> o }
  )
  convert<String, String>(
    { v -> v },
    { o -> o }
  )
}
