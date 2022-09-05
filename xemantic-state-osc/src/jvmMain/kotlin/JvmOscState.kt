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
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.transport.OSCPortInBuilder
import com.illposed.osc.transport.OSCPortOutBuilder
import com.xemantic.state.State
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.net.InetSocketAddress
import kotlin.reflect.KType
import kotlin.reflect.full.createType

actual fun oscState(
  builder: OscState.Builder.() -> Unit
): OscState = JvmOscState(builder)

private class JvmOscState(builder: OscState.Builder.() -> Unit) : OscState {

  private val logger = KotlinLogging.logger {}

  private val config = OscState.Builder().also { builder(it) }

  private val oscIn = OSCPortInBuilder().apply {
    setLocalPort(config.port)
    config.ip?.let {
      setLocalSocketAddress(
        InetSocketAddress(it, config.port)
      )
    }
  }.build()

  private val converters = config.converters

  init {
    oscIn.startListening()
  }

  private val exportedStateMap = mutableMapOf<String, State<*>>()

  private val exportedConverters = mutableMapOf<String, OscPropertyConverter<Any, Any>>()

  override fun <T> export(
    address: String,
    state: State<T>,
    emitAllProperty: String
  ) {
    logger.debug {
      "Exporting state at: udp://${config.ip ?: "localhost"}:${config.port}$address"
    }
    state.visitProperties { property ->
      if (property.valueFlow != null) {
        val type = property.kProperty.returnType
        val converter = converters[type] ?: throw IllegalStateException(
          "No converter for property: ${property.path}, type $type"
        )
        exportedConverters["$address/${property.path.joinToString("/")}"] =
          converter as OscPropertyConverter<Any, Any>
      }
    }
    exportedStateMap[address] = state
    oscIn.dispatcher.addListener(
      object : MessageSelector {
        override fun isInfoRequired() = false
        override fun matches(messageEvent: OSCMessageEvent) =
          messageEvent.message.address.startsWith(address)
      }
    ) { event ->
      logger.debug {
        "Received message at: ${event.message.address}"
      }
      val path = event.message.address
        .removePrefix("$address/")
        .split("/")

      val args = event.message.arguments
      val value = if (args.size == 1) args[0] else args
      val converter = exportedConverters[event.message.address]
      if (converter == null) {
        logger.error { "Received message for unsupported address: ${event.message.address}" }
      } else {
        val decoded = exportedConverters[event.message.address]!!.decode(value)
        val change = State.Change(
          path = path,
          source = "osc", // TODO how to get address of the source?
          current = decoded,
          previous = decoded
        )
        try {
          state.update(change)
        } catch (e: IllegalArgumentException) {
          logger.error(e) { "Cannot update " }
        }
      }
    }
  }

  override fun output(
    builder: OscState.Output.Builder.() -> Unit
  ) = object : OscState.Output {

    private val config = OscState.Output.Builder().also { builder(it) }

    private val oscOut = OSCPortOutBuilder().apply {
      // TODO better handling of source ip
      setRemotePort(config.port)
      config.ip?.let {
        setRemoteSocketAddress(
          InetSocketAddress(it, config.port)
        )
      }
    }.build()

    override fun emitOnChanges(address: String, remoteAddress: String?) {
      GlobalScope.launch { // TODO change the scope
        exportedStateMap[address]!!.changeFlow.collect {
          val propertyAddress = it.path.joinToString("/")
          // TODO conversion here
          val converter = exportedConverters["$address/$propertyAddress"]!!
          val arg = converter.encode((it as State.Change<Any>).current)
          oscOut.send(
            OSCMessage(
              "$address/$propertyAddress",
              maybeWrap(arg)
            )
          )
        }
      }
    }

    override fun close() {
      oscOut.disconnect()
      oscOut.close()
    }

  }

  override fun close() {
    logger.info { "Closing OSC at udp://${config.ip ?: "localhost"}:${config.port}" }
    oscIn.stopListening()
    oscIn.close()
  }

}

actual fun oscSender(
  builder: OscSender.Builder.() -> Unit
): OscSender = JvmOscSender(builder)

private class JvmOscSender(
  builder: OscSender.Builder.() -> Unit
) : OscSender {

  private val config = OscSender.Builder().also { builder(it) }

  private val oscOut = OSCPortOutBuilder().apply {
    // TODO better handling of source ip
    setRemotePort(config.port)
    config.ip?.let {
      setRemoteSocketAddress(
        InetSocketAddress(it, config.port)
      )
    }
  }.build()

  private val converters: Map<KType, OscPropertyConverter<Any, Any>> =
    config.converters as Map<KType, OscPropertyConverter<Any, Any>>

  init {
    oscOut.connect()
  }

  override fun <T> send(address: String, value: T) {
    val type: KType = value!!::class.createType()
    val converter = converters[type] ?: throw IllegalStateException(
      "No converter for type: $type"
    )
    val arg = converter.encode(value)
    oscOut.send(OSCMessage(address, maybeWrap(arg)))
  }

  override fun close() {
    oscOut.disconnect()
    oscOut.close()
  }

}

private fun <T> maybeWrap(x: T) = if (x is List<*>) x else listOf(x)
