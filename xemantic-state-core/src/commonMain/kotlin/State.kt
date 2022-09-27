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
 * Creates new state tracking changes to properties of the specified entity instance.
 * The entity instance is created with supplied [State.Builder] which
 * supports hierarchical properties.
 *
 * @param T the type of the [entity] for which this state object is tracking changes.
 * @param changeSource the source of changes, if not specified, it will default
 *          to the instance of state created by this function.
 * @param block thee block of code creating the entity associated with this state instance.
 */
class State<T>(
  changeSource: Any? = null,
  block: (sb: State.Builder) -> T
) {

  /**
   * The source applied to all the [Change.source]s originating from this state.
   *
   * Note: if not specified, it will default to this sate instance itself.
   */
  val changeSource: Any = changeSource ?: this

  // can it be replaced with regular flow but shared?
  private val _changeFlow = MutableSharedFlow<Change<*>>(
    extraBufferCapacity = 1000 // this value is arbitrary
  )

  private val entityState = EntityState(emptyList())

  /**
   * The entity tracked by this state.
   */
  val entity: T = block(entityState)

  /**
   * Children of this state.
   */
  val children: Map<String, Property<*>> = entityState.establishChildren()

  /**
   * The flow of state changes.
   */
  val changeFlow: Flow<Change<*>> = _changeFlow

  /**
   * The property being tracked.
   *
   * @param V the type of this property.
   */
  inner class Property<V>(

    /**
     * The path of this property.
     * Might consist of multiple strings if property is nested in hierarchy.
     */
    val path: List<String>,

    /**
     * Initial value assigned to this property.
     *
     * @see setInitialValue
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
     * Note: if the property is a leaf, the [children] map will be `null`.
     *
     * @see isLeaf
     */
    val children: Map<String, Property<*>>? = null,

  ) {

    /**
     * `true` if the property is a leaf. It
     * has a value but no children.
     */
    val isLeaf: Boolean = (children == null)

    private val _valueFlow: MutableStateFlow<V>? =
      if (isLeaf) MutableStateFlow(initialValue)
      else null

    var value: V
      get() = if (isLeaf) _valueFlow!!.value else initialValue
      set(value) { updateValue(value) }

    /**
     * Value flow.
     *
     * Note: if the property is a leaf, the [valueFlow] is not null, and
     * the [children] map will be empty.
     *
     * @see isLeaf
     */
    val valueFlow: StateFlow<V>? = _valueFlow

    override fun toString() =
      "State.Property[path=${path.joinToString(".")}, " +
          if (isLeaf) {
            "value=${_valueFlow!!.value}, initialValue=$initialValue]"
          } else {
            "children=${children!!.keys}"
          }

    internal val readWriteProperty = object : ReadWriteProperty<Any, V> {

      override fun getValue(
        thisRef: Any,
        property: KProperty<*>
      ): V = _valueFlow!!.value

      override fun setValue(
        thisRef: Any,
        property: KProperty<*>,
        value: V
      ) {
        updateValue(value)
      }

    }

    internal fun updateValue(
      value: V,
      source: Any = changeSource
    ) {
      assertIsLeaf()
      _valueFlow!!.update { previous ->
        if (previous != value) {
          _changeFlow.tryEmit(
            State.Change(
              path = path,
              source = source,
              previous = previous,
              current = value
            )
          )
        }
        value
      }
    }

    fun emit(source: Any) {
      assertIsLeaf()
      val value = _valueFlow!!.value
      _changeFlow.tryEmit(
        State.Change(
          path = path,
          source = source,
          previous = value,
          current = value
        )
      )
    }

    fun setInitialValue() {
      updateValue(initialValue)
    }

    private fun assertIsLeaf() {
      if (!isLeaf) {
        throw IllegalStateException(
          "Cannot update property which is not a leaf: $path"
        )
      }
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
   * Finds property at given [path].
   *
   * @param V the expected type of property, saves casting.
   * @param path the property path.
   * @return state property.
   * @throws IllegalArgumentException if the path is empty, or there is no such property.
   *          (existing properties can be inspected with [children] map.
   */
  fun <V> findProperty(vararg path: String): Property<V> = findProperty(listOf(*path))

  /**
   * Finds property at given [path].
   *
   * @param V the expected type of property, saves casting.
   * @param path the property path.
   * @return state property.
   * @throws IllegalArgumentException if the path is empty, or there is no such property.
   *          (existing properties can be inspected with [children] map.
   */
  fun <V> findProperty(
    path: List<String>
  ): Property<V> {
    require(path.isNotEmpty()) { "Cannot find property with empty path" }
    return children.findProperty(path, 0)
  }

  /**
   * Updates property with given [change].
   *
   * @param V the type of changed property.
   * @param change the value change to apply to this state.
   * @throws IllegalArgumentException if the [Change.path] does not exist in this state
   * @throws ClassCastException if the type of change is not the same as the type of
   *          property in this state having the same [Change.path].
   */
  fun <V> update(change: State.Change<V>) {
    if (change.source != changeSource) {
      findProperty<V>(change.path).updateValue(
        value = change.current,
        source = change.source
      )
    }
  }

  /**
   * Emits current values of all the properties.
   * They will be emitted as [Change]s collectible through the [changeFlow].
   *
   * @param source the optional source of this change, if not specified
   *          it will default to the [changeSource].
   */
  fun emit(source: Any = changeSource) {
    visitProperties { property ->
      if (property.isLeaf) {
        property.emit(source)
      }
    }
  }

  /**
   * Visits all the properties in the order of declaration.
   */
  fun visitProperties(
    visitor: (property: Property<*>) -> Unit
  ) {
    children.visitProperties(visitor)
  }

  fun setInitialValues() {
    visitProperties {
      if (it.isLeaf) {
        it.setInitialValue()
      }
    }
  }

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

  private fun <V> Map<String, Property<*>>.findProperty(
    path: List<String>,
    level: Int
  ): Property<V> {
    val property = this[path[level]]
      ?: throw IllegalArgumentException("No such property, path: $path")
    @Suppress("UNCHECKED_CAST")
    return (
        if (level == (path.size - 1)) property as Property<V>
        else property.children!!.findProperty(path, level + 1)
    )
  }

  private fun Map<String, Property<*>>.visitProperties(
    visitor: (property: Property<*>) -> Unit
  ) {
    for (property in values) {
      visitor(property)
      property.children?.visitProperties(visitor)
    }
  }

  private inner class EntityState(
    val path: List<String>
  ) : State.Builder {

    private val properties = mutableListOf<Property<*>>()

    fun establishChildren() = properties.associateBy {
      it.path.last()
    }

    override fun <X, V> property(
      value: V,
      metadata: Any?
    ) = PropertyDelegateProvider<X, ReadWriteProperty<X, V>> { _, kProperty ->
      val property = Property(
        path = path + kProperty.name,
        initialValue = value,
        kProperty = kProperty,
        metadata = metadata
      )
      properties.add(property)
      @Suppress("UNCHECKED_CAST")
      property.readWriteProperty as ReadWriteProperty<X, V>
    }

    override fun <X, V> child(
      metadata: Any?,
      block: (sb: State.Builder) -> V,
    ) = PropertyDelegateProvider<X, ReadOnlyProperty<X, V>> { _, kProperty ->

      val propertyPath = path + kProperty.name
      val entityState = EntityState(propertyPath)
      val entity = block(entityState)
      @Suppress("UNCHECKED_CAST")
      val stateProperty = Property(
        path = propertyPath,
        initialValue = entity,
        kProperty = kProperty as KProperty<V>,
        metadata = metadata,
        children = entityState.establishChildren()
      )
      properties.add(stateProperty)
      ReadOnlyProperty { _, _ -> entity }
    }

  }

}
