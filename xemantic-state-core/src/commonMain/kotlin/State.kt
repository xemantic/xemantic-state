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

package com.xemantic.state

import kotlinx.coroutines.flow.*
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StateProperty<T>(
  val path: List<String>,
  val metadata: KProperty<T>,
  val propertyMap: Map<String, StateProperty<*>> = emptyMap(),
  val valueFlow: Flow<T>? = null
)

data class ValueChange<T>(
  val path: List<String>,
  val source: Any,
  val current: T,
  val previous: T
)

fun <T> state(block: (sb: StateBuilder) -> T): State<T> = DefaultState(block)

/**
 * State of an entity. The state is tracking changes to entity properties,
 * including hierarchical tracking of children properties.
 *
 * @param T the type of the entity for which this state object is tracking changes.
 */
interface State<T> {

  val entity: T

  val valueChangeFlow: Flow<ValueChange<*>>

  val propertyMap: Map<String, StateProperty<*>>

  /**
   * Finds property at given [path].
   *
   * @param path the property path.
   * @return state property.
   * @throws IllegalArgumentException if there is no such property.
   */
  fun findProperty(path: List<String>): StateProperty<*>

  /**
   * Updates property with given [change].
   *
   * @param change the value change to apply to this state.
   * @return `true` if there is such a property, `false` otherwise.
   * @throws IllegalArgumentException if there is no such property.
   */
  fun update(change: ValueChange<*>)

  /**
   * Emits the current values of all the properties to [valueChangeFlow]
   */
  fun emit()

}

interface StateBuilder {

  fun <T, V> property(initialValue: V): PropertyDelegateProvider<T, ReadWriteProperty<T, V>>

  fun <T, V> child(block: (sb: StateBuilder) -> V): PropertyDelegateProvider<T, ReadOnlyProperty<T, V>>

}

private class DefaultState<T>(block: (sb: StateBuilder) -> T) : State<T> {

  private val mutableValueChangeFlow = MutableSharedFlow<ValueChange<*>>(
    extraBufferCapacity = 100
  )

  private inner class EntityState(private val path: List<String>) : StateBuilder {

    private val properties = mutableListOf<StateProperty<*>>()

    lateinit var propertyMap: Map<String, StateProperty<*>>

    override fun <T, V> property(initialValue: V) =
      PropertyDelegateProvider<T, ReadWriteProperty<T, V>> { _, property ->
        val propertyPath = path + property.name
        val valueFlow = MutableStateFlow(initialValue)
        properties.add(
          StateProperty(
            path = propertyPath,
            metadata = property as KProperty<V>,
            valueFlow = valueFlow
          )
        )
        object : ReadWriteProperty<T, V> {
          override fun getValue(thisRef: T, property: KProperty<*>): V = valueFlow.value
          override fun setValue(thisRef: T, property: KProperty<*>, value: V) {
            // TODO what if the same value is updated twice?
            valueFlow.update { previousValue ->
              mutableValueChangeFlow.tryEmit(
                ValueChange(
                  path = propertyPath,
                  source = this@DefaultState,
                  current = value,
                  previous = previousValue
                )
              )
              value
            }
          }
        }
      }

    override fun <T, V> child(block: (sb: StateBuilder) -> V) =
      PropertyDelegateProvider<T, ReadOnlyProperty<T, V>> { _, property ->
        val propertyPath = path + property.name
        val entityState = EntityState(propertyPath)
        val entity = block(entityState)
        entityState.calculatePropertyMap()
        properties.add(
          StateProperty(
            path = propertyPath,
            metadata = property as KProperty<V>,
            propertyMap = entityState.propertyMap
          )
        )
        ReadOnlyProperty { _, _ -> entity }
      }

    fun calculatePropertyMap() {
      propertyMap = properties.associateBy { it.path.first() }
    }

  }

  private val entityState = EntityState(emptyList())

  override val entity: T = block(entityState)
  init {
    entityState.calculatePropertyMap()
  }

  override val valueChangeFlow: Flow<ValueChange<*>> = mutableValueChangeFlow

  override val propertyMap: Map<String, StateProperty<*>> = entityState.propertyMap

  override fun findProperty(path: List<String>): StateProperty<*> =
    entityState.propertyMap.findProperty(path)

  override fun update(change: ValueChange<*>) {
    val flow: MutableStateFlow<Any> = findProperty(change.path).valueFlow as MutableStateFlow<Any>
    flow.value = change.current as Any
  }

  override fun emit() {
    propertyMap.emit()
  }

  private fun Map<String, StateProperty<*>>.findProperty(path: List<String>): StateProperty<*> {
    check(path.isNotEmpty()) { "path cannot be empty" }
    val property = this[path.first()]
    check(property != null) { "no such property: $path" }
    return (
        if (property.propertyMap.isEmpty()) property
        else findProperty(path.subList(1, path.size - 1))
    )
  }

  private fun Map<String, StateProperty<*>>.emit() {
    values.forEach {
      if (it.valueFlow != null) {
        val value = (it.valueFlow as StateFlow<*>).value
        mutableValueChangeFlow.tryEmit(
          ValueChange(
            path = it.path,
            source = this,
            current = value,
            previous = value
          )
        )
      } else {
        it.propertyMap.emit()
      }
    }
  }

}
