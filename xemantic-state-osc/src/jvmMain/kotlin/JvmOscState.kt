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

import com.illposed.osc.MessageSelector
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector
import com.illposed.osc.transport.OSCPortInBuilder
import com.illposed.osc.transport.OSCPortOutBuilder
import com.xemantic.state.State
import java.net.InetSocketAddress
import kotlin.reflect.KType

actual fun oscState(
  ip: String?,
  port: Int,
  state: State<*>,
  converters: Map<KType, Converter<*>>
): OscState = JvmOscState(ip, port, state, converters)

private class JvmOscState(
  ip: String?,
  port: Int,
  private val state: State<*>,
  private val converters: Map<KType, Converter<*>>
) : OscState {

  val oscIn = OSCPortInBuilder().apply {
    setLocalPort(port)
    if (ip != null) {
      setLocalSocketAddress(InetSocketAddress(ip, port))
    }
  }.build()

//  private val oscOuts = mutableMapOf<ConnectionSpec, OSCPortOut>()

  init {
    oscIn.startListening()
  }

  override fun <T> exportState(
    address: String,
    emitAllTriggerAddress: String,
    state: State<T>
  ) {
    oscIn.dispatcher.addListener(
      object : MessageSelector {
        override fun isInfoRequired() = false
        override fun matches(messageEvent: OSCMessageEvent) =
          messageEvent.message.address.startsWith(address)
      }
    ) { event ->
      val path = event.message.address
        .removePrefix("$address/")
        .split("/")
      try {
        val property = state.findProperty(path)
        val type = property.kProperty.returnType
        val converter = converters[type] as Converter<Any>?
        if (converter != null) {
          val value = converter.fromOsc(event.message.arguments)
          state.update(
            State.Change(
              path = path,
              source = "osc",
              current = value,
              previous = value
            )
          )
        } else {
          println("No converter for property: $property")
        }
      } catch (e: IllegalArgumentException) {
        println(e)
      }
    }
  }

  override fun emitChanges(
    ip: String,
    port: Int,
    localStateAddress: String,
    remoteStateAddress: String
  ) {
    oscIn.dispatcher.addListener(
      OSCPatternAddressMessageSelector("/$localStateAddress/*")
    ) {
      val oscOut = OSCPortOutBuilder()
        .setRemotePort(port)
        .setRemoteSocketAddress(InetSocketAddress(ip, port))
        .build()
    }
  }

  override fun close() {
    oscIn.stopListening()
    oscIn.close()
    // TODO finish close
//    oscOuts.forEach {
//      it.
//    }
  }

//  private data class ConnectionSpec(
//    val host: String,
//    val port: Int
//  )

}
