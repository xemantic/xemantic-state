/*
 * xemantic-state - kotlin library for transforming state beans into
 * reactive event streams
 * Copyright (C) 2020  Kazimierz Pogoda
 *
 * This file is part of xemantic-state.
 *
 * xemantic-state  is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * xemantic-state is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with xemantic-state.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.xemantic.state.core

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.kotlin.toObservable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class StateKeeper<S : Any, E : Any>(
    stateClass: KClass<S>,
    eventsClass: KClass<E>,
    scheduler: Scheduler
) {

  private val stateChangeSubject = PublishSubject.create<StateChangeEvent<Any>>()

  val stateChangeEvents: Observable<StateChangeEvent<Any>> =
      stateChangeSubject.subscribeOn(scheduler)

  private val stateMap: Map<String, Property<Any>>

  private val stateFactory = object : State<S> {

    override fun <T> mutableProperty(value: T): ReadWriteProperty<S, T> =
        MutableStateProperty(value)

    override fun <T> property(value: T) = StateProperty<S, T>(value)

  }

  private val eventsFactory = object : Events<E> {
    override fun <T> events(): ReadOnlyProperty<E, Observable<T>> = EventsProperty(scheduler)
  }

  val state: S = stateClass::constructors.get().first().call(stateFactory)

  val events: E = eventsClass::constructors.get().first().call(eventsFactory)


  init {
    @Suppress("UNCHECKED_CAST")
    stateMap = stateClass.memberProperties
        .filter { it.visibility == KVisibility.PUBLIC }
        .filterIsInstance<KProperty1<S, Any>>()
        .map { property ->
          property.isAccessible = true
          val stateProperty =
              property.getDelegate(state) as StateProperty<S, Any>
          val entry = Property<Any>(property.name, stateProperty.valueRef, stateChangeSubject)
          stateProperty.property = entry
          Pair(property.name, entry)
        }
        .toMap()

    @Suppress("UNCHECKED_CAST")
    eventsClass.memberProperties
        .filter { it.visibility == KVisibility.PUBLIC }
        .filterIsInstance<KProperty1<E, Observable<Any>>>()
        .forEach { property ->
          property.isAccessible = true
          val eventsProperty =
              (property.getDelegate(events) as EventsProperty<E, Any>)
          val entry = stateMap[property.name]!!
          entry.events = eventsProperty.events
          eventsProperty.property = property.name
          entry.publishEvent()
        }
  }

  fun externalChange(property: String, value: Any) =
      stateMap[property]!!.externalChange(value)

  /**
   * Will publish values of all properties as [StateChangeEvent]s.
   */
  fun publish() =
      stateMap.values.forEach { it.publishStateChangeEvent() }

  fun properties() = stateMap.keys.toObservable()

}

interface State<S : Any> {

  fun <T> mutableProperty(value: T): ReadWriteProperty<S, T>

  fun <T> property(value: T): ReadOnlyProperty<S, T>

}

interface Events<E : Any> {

  fun <T> events(): ReadOnlyProperty<E, Observable<T>>

}

data class StateChangeEvent<T>(
    val property: String,
    val value: T
)

private open class StateProperty<S : Any, T>(value: T) : ReadOnlyProperty<S, T> {

  internal lateinit var property: Property<T>

  internal val valueRef = AtomicReference(value)

  override fun getValue(thisRef: S, property: KProperty<*>): T = valueRef.get()

  override fun toString(): String = valueRef.toString()

}

private class MutableStateProperty<S : Any, T>(value: T)
  : StateProperty<S, T>(value), ReadWriteProperty<S, T> {

  override fun setValue(thisRef: S, property: KProperty<*>, value: T) {
    this.property.value = value
  }

}

private class EventsProperty<E, T>(scheduler: Scheduler) : ReadOnlyProperty<E, Observable<T>> {

  internal lateinit var property: String

  internal val events: Subject<T> = BehaviorSubject.create()

  private val eventsValue = events.subscribeOn(scheduler)

  override fun getValue(thisRef: E, property: KProperty<*>): Observable<T> = eventsValue

  override fun toString(): String = "Observable[${property}]"

}

private class Property<T>(
    internal val name: String,
    private val valueRef: AtomicReference<T>,
    val stateChangeSubject: Subject<StateChangeEvent<Any>>
) {

  private val logger = KotlinLogging.logger {}

  internal lateinit var events: Subject<T>

  internal var value: T
    get() = valueRef.get()
    set(value) {
      val oldValue = valueRef.getAndSet(value)
      if (value != oldValue) {
        logger.trace { "State change: [$name:$oldValue->$value]" }
        events.onNext(value)
        stateChangeSubject.onNext(StateChangeEvent(name, value as Any))
      }
    }

  internal fun publishEvent() {
    val value = valueRef.get()
    logger.debug { "Publishing: $name=$value" }
    events.onNext(value)
  }

  internal fun publishStateChangeEvent() {
    val event = StateChangeEvent(name, valueRef.get() as Any)
    logger.debug { "Publishing: $event" }
    stateChangeSubject.onNext(event)
  }

  internal fun externalChange(value: T) {
    val oldValue = valueRef.getAndSet(value)
    if (value != oldValue) {
      logger.trace {
        "State change from external source, [$name:$oldValue->$value], no StateChangeEvent"
      }
      events.onNext(value)
    }
  }

}
