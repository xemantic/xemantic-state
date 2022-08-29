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

import com.illposed.osc.OSCMessage
import com.illposed.osc.transport.OSCPortOutBuilder
import kotlin.reflect.KType
import kotlin.reflect.typeOf

val defaultConverters = mapOf<KType, Converter<*>>(
  typeOf<Double>() to DoubleConverter(),
  typeOf<Int>() to IntConverter(),
  typeOf<Boolean>() to BooleanConverter()
)

fun oscOut(localPort: Int, remotePort: Int, block: OscOut.() -> Unit) {
  OscOut(localPort, remotePort).use {
    block(it)
    Thread.sleep(100) // give some time for sending
  }
}

class OscOut(localPort: Int, remotePort: Int) : AutoCloseable {

  val oscOut = OSCPortOutBuilder()
    .setLocalPort(localPort)
    .setRemotePort(remotePort)
    .build()

  inline fun <reified T> send(address: String, value: T) {
    oscOut.transport.send(
      OSCMessage(
        address,
        (defaultConverters[typeOf<T>()] as Converter<T>).toOsc(value)
      )
    )
  }

  override fun close() {
    oscOut.close()
  }

}
