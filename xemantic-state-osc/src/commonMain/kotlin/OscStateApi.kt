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

import com.xemantic.osc.*
import com.xemantic.state.State
import io.ktor.utils.io.core.*
import kotlinx.coroutines.launch
import mu.KotlinLogging

fun statefulOsc(
  build: StatefulOsc.Builder.() -> Unit
) = StatefulOsc(build)

class StatefulOsc(
  private val build: Builder.() -> Unit
) : Closeable {

  private val logger = KotlinLogging.logger {}

  internal class Export<T>(
    val state: State<T>,
    var subscribers: List<Subscriber>
  )

  internal class Subscriber(
    val receivingAddress: String,
    val output: Osc.Output,
  )

  internal data class Subscription(
    val address: String,
    val receivingAddress: String
  )

  class Builder {

    internal var oscBuild: Osc.Builder.() -> Unit = {}

    fun osc(build: Osc.Builder.() -> Unit) {
      oscBuild = build
    }

    fun <T> export(address: String, state: State<T>) {
      exports[address] = Export(
          state = state,
          mutableListOf()
      )
    }
    internal val exports = mutableMapOf<String, Export<*>>()
  }

  private val builder = Builder().also { build(it) }

  private val exports = builder.exports

  val osc = osc {
    converters += converters {

      convert<Subscription>(
        encode = { x , output ->
          output.writeTypeTag("ss")
          output.writeOscString(x.address)
          output.writeOscString(x.receivingAddress)
        },
        decode = { _, input ->
          Subscription(
            input.readOscString(),
            input.readOscString()
          )
        }
      )

    }
    conversion<Subscription>("/subscribe")
    conversion<String>("/unsubscribe")
    exports.forEach { address, export ->
      export.state.visitProperties { property ->
        if (property.isLeaf) { // TODO change to isLeaf
          conversion(
            address + "/" + property.path.joinToString("/"),
            property.kProperty.returnType
          )
        }
      }
    }
    builder.oscBuild(this)
  }

  val messageFlowJob = osc.coroutineScope.launch {
    osc.messageFlow.collect { message ->
      try {
        handleMessage(message)
      } catch (e : Exception) {
        logger.error(e) { "OSC Message error" }
      }
    }
  }

  init {
    for (export in exports.values) {
      osc.coroutineScope.launch {
        export.state.changeFlow.collect { change ->
          val propertyAddress = change.path.joinToString("/")
          export.subscribers
            .filter {
              change.source != "${it.output.hostname}:${it.output.port}"
            }
            .forEach { subscriber ->
              subscriber.output.send(
                address = "${subscriber.receivingAddress}/$propertyAddress",
                value = change.current
              )
            }
        }
      }
    }
  }

  fun output(
    build: Osc.Output.Builder.() -> Unit
  ): Osc.Output = osc.output {
    // TODO move to common function
    conversion<Subscription>("/subscribe")
    conversion<String>("/unsubscribe")
    build(this)
  }

  private fun handleMessage(message: Osc.Message<*>) {
    if (message.address.startsWith("/subscribe")) {
      subscribeRemote(message)
    } else if (message.address.startsWith("/unsubscribe")) {
      unsubscribeRemote(message)
    } else {
      val path = message.address
        .split("/")
        .let { it.subList(1, it.size) }
      val export = exports["/" + path.first()]
      if (export != null) {
        val propertyPath = path.subList(1, path.size)
        val change = State.Change(
          path = propertyPath,
          source = "${message.hostname}:${message.port}",
          current = message.value,
          previous = message.value
        )
        export.state.update(change)
      }
    }
  }

  private fun subscribeRemote(message: Osc.Message<*>) {
    val subscription = message.value as Subscription
    subscribeRemote(
      hostname = message.hostname,
      port = message.port,
      address = subscription.address,
      receivingAddress = subscription.receivingAddress
    )
  }

  fun subscribeRemote(
    hostname: String,
    port: Int,
    address: String,
    receivingAddress: String = address
  ) {
    logger.info {
      "Subscribing $address -> $hostname:$port$receivingAddress"
    }
    val export = requireExport(address)
    if (!export.subscribers.any {
      it.receivingAddress == receivingAddress
          && osc.hostname == hostname
          && osc.port == port
    }) {
      export.subscribers += Subscriber(
        receivingAddress,
        osc.output {
          this.hostname = hostname
          this.port = port
          export.state.visitProperties { property ->
            if (property.isLeaf) {
              conversion(
                receivingAddress + "/" + property.path.joinToString("/"),
                property.kProperty.returnType
              )
            }
          }
        }
      )
    } else {
      logger.warn {
        "Already subscribed $address -> $hostname:$port$receivingAddress"
      }
    }
  }

  private fun unsubscribeRemote(message: Osc.Message<*>) {
    unsubscribeRemote(
      hostname = message.hostname,
      port = message.port,
      address = message.value as String
    )
  }

  fun unsubscribeRemote(
    hostname: String,
    port: Int,
    address: String
  ) {
    logger.info {
      "Unsubscribing $address -> $hostname:$port"
    }
    val export = requireExport(address)
    val mutableSubscribers = export.subscribers.toMutableList()
    val removed = mutableSubscribers.removeIf {
      it.output.hostname == hostname
          && it.output.port == port
    }
    if (!removed) {
      logger.warn {
        "Cannot unsubscribe, no subscriber for $address -> $hostname:$port"
      }
    }
    export.subscribers = export.subscribers.toList()
  }

  override fun close() {
    osc.close()
  }

  private fun requireExport(
    address: String
  ) = exports[address] ?: throw IllegalArgumentException(
    "No state exported under address: $address"
  )

}

fun <T> Osc.ConversionBuilder.stateConversions(
  address: String,
  state: State<T>
) {
  state.visitProperties {
    conversion(
      "$address/${it.path.joinToString("/")}",
      it.kProperty.returnType
    )
  }
}

fun Osc.Output.subscribe(address: String, receivingAddress: String = address) {
  send("/subscribe", StatefulOsc.Subscription(address, receivingAddress))
}

fun Osc.Output.unsubscribe(address: String) {
  send("/unsubscribe", address)
}
