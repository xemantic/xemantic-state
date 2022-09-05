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

/**
 * State of an entity. The state is tracking changes to entity properties,
 * including hierarchical tracking of children properties.
 *
 * @param T the type of the [entity] for which this state object is tracking changes.
 */
interface State<T> {

  /**
   * The property being tracked.
   */
  class Property<V>(

    /**
     * The path of this property.
     * Might consist of multiple strings if property is nested in hierarchy.
     */
    val path: List<String>,

    /**
     * Initial value assigned to this property.
     * Might be useful for resetting the value to canonical state.
     */
    val initialValue: V,

    /**
     * The type of this property.
     */
    val kProperty: KProperty<V>,

    /**
     * Optional custom metadata object associated with this property.
     */
    val metadata: Any? = null,

    /**
     * Children map, mapping property name to property definition.
     *
     * Note: if the property is a leaf, the [children] map will be always empty,
     * however the opposite is not true, as it is technically possible to
     * create a child object without properties. The null [valueFlow] instead, and vice-versa.
     */
    val children: Map<String, Property<*>> = emptyMap(),

    internal val mutableValueFlow: MutableStateFlow<V>? = null,

    /**
     * Value flow.
     * If the property is a leaf, the [valueFlow] is not null, and
     * the [children] map will be empty.
     */
    val valueFlow: Flow<V>? = mutableValueFlow,

  ) {

    override fun toString() =
      "State.Property[path=${path.joinToString(".")}, " +
          if (mutableValueFlow != null) {
            "value=${mutableValueFlow.value}, initialValue=$initialValue]"
          } else {
            "children=${children.keys}"
          }

  }

  /**
   * State change.
   * Note: it is a data class designed to be serializable.
   */
  data class Change<V>(

    /**
     * Property path.
     */
    val path: List<String>,

    /**
     * Property source.
     */
    val source: Any,

    /**
     * Current value.
     */
    val current: V,

    /**
     * Previous value.
     */
    val previous: V

  ) {

    override fun toString() =
      "State.Change[path=${path.joinToString(".")}, " +
          "source=$source, current=$current, previous=$previous]"

  }

  /**
   * The entity tracked by this state.
   *
   * Note: the [entity] is provided here once [state] function is called.
   */
  val entity: T

  /**
   * The source applied to all the [Change.source]s originating from this state.
   *
   * Note: if not specified in the [state] function, it will default to the sate itself.
   */
  val changeSource: Any

  /**
   * The flow of state changes.
   */
  val changeFlow: Flow<Change<*>>

  /**
   * Children of this state.
   */
  val children: Map<String, Property<*>>

  /**
   * Finds property at given [path].
   *
   * @param path the property path.
   * @return state property.
   * @throws IllegalArgumentException if the path is empty, or there is no such property.
   *          (existing properties can be inspected with [children] map.
   */
  fun findProperty(path: List<String>): Property<*>

  /**
   * Updates property with given [change].
   *
   * @param V the type of changed property.
   * @param change the value change to apply to this state.
   * @throws IllegalArgumentException if the [Change.path] does not exist in this state
   * @throws ClassCastException if the type of change is not the same as the type of
   *          property in this state having the same [Change.path].
   */
  fun <V> update(change: Change<V>)

  /**
   * Emits current values of all the properties.
   * They will be emitted as [Change]s collectible through the [changeFlow].
   */
  fun emit()

  /**
   * Visits all the properties in the order of declaration.
   */
  fun visitProperties(
    visitor: (property: Property<*>) -> Unit
  )

  /**
   * State builder.
   */
  interface Builder {

    /**
     * Creates new property delegate for mutable property.
     *
     * @param value the value assigned to the property.
     * @param metadata the metadata object associated with this property.
     * @return property delegate provider.
     */
    fun <T, V> property(
      value: V,
      metadata: Any? = null
    ): PropertyDelegateProvider<T, ReadWriteProperty<T, V>>

    /**
     * Creates new property delegate for child property.
     *
     * @param metadata the metadata object associated with this property.
     * @param block the block of code creating new property instance using
     *          supplied nested state builder.
     * @return property delegate provider.
     */
    fun <T, V> child(
      metadata: Any? = null,
      block: (state: State.Builder) -> V,
    ): PropertyDelegateProvider<T, ReadOnlyProperty<T, V>>

  }

}

/**
 * Creates new state associated with specified entity instance.
 * The entity instance is created with supplied [State.Builder] instance.
 *
 * @param changeSource the source of changes, if not specified, it will default
 *          to the instance of state created by this function.
 * @param block thee block of code creating the entity associated with this state instance.
 */
fun <T> state(
  changeSource: Any? = null,
  block: (state: State.Builder) -> T
): State<T> = DefaultState(
  changeSource,
  block
)
