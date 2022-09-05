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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class DefaultState<T>(
  changeSource: Any?,
  block: (sb: State.Builder) -> T
) : State<T> {

  override val changeSource = changeSource ?: this

  private val mutableValueChangeFlow = MutableSharedFlow<State.Change<*>>(
    extraBufferCapacity = 1000
  )

  private inner class EntityState(
    val path: List<String>
  ) : State.Builder {

    private val properties = mutableListOf<State.Property<*>>()

    fun establishChildren() = properties.associateBy {
      it.path.last()
    }

    override fun <X, V> property(
      value: V,
      metadata: Any?
    ) = PropertyDelegateProvider<X, ReadWriteProperty<X, V>> { _, property ->

      val propertyPath = path + property.name
      val mutableValueFlow = MutableStateFlow(value)

      @Suppress("UNCHECKED_CAST")
      val stateProperty = State.Property(
        path = propertyPath,
        initialValue = value,
        kProperty = property as KProperty<V>,
        metadata = metadata,
        mutableValueFlow = mutableValueFlow
      )

      properties.add(stateProperty)
      object : ReadWriteProperty<X, V> {

        override fun getValue(
          thisRef: X,
          property: KProperty<*>
        ): V = mutableValueFlow.value

        override fun setValue(
          thisRef: X, property: KProperty<*>,
          value: V
        ) {
          mutableValueFlow.update { previousValue ->
            if (value != previousValue) {
              mutableValueChangeFlow.tryEmit(
                State.Change(
                  path = propertyPath,
                  source = this@DefaultState,
                  current = value,
                  previous = previousValue
                )
              )
            }
            value
          }
        }

      }

    }

    override fun <X, V> child(
      metadata: Any?,
      block: (sb: State.Builder) -> V,
    ) = PropertyDelegateProvider<X, ReadOnlyProperty<X, V>> { _, property ->

      val propertyPath = path + property.name
      val entityState = EntityState(propertyPath)
      val entity = block(entityState)
      @Suppress("UNCHECKED_CAST")
      val stateProperty = State.Property(
        path = propertyPath,
        initialValue = entity,
        kProperty = property as KProperty<V>,
        metadata = metadata,
        children = entityState.establishChildren()
      )
      properties.add(stateProperty)
      ReadOnlyProperty { _, _ -> entity }
    }

  }

  private val entityState = EntityState(emptyList())

  override val entity = block(entityState)

  override val children = entityState.establishChildren()

  override val changeFlow = mutableValueChangeFlow

  override fun findProperty(
    path: List<String>
  ): State.Property<*> {
    require(path.isNotEmpty()) { "Cannot find property with empty path" }
    return children.findProperty(path, 0)
  }

  override fun <V> update(change: State.Change<V>) {
    @Suppress("UNCHECKED_CAST")
    val flow = findProperty(change.path).mutableValueFlow as MutableStateFlow<V>
    flow.update { previous ->
      if (change.current != previous) {
        mutableValueChangeFlow.tryEmit(
          State.Change(
            path = change.path,
            source = change.source,
            previous = previous,
            current = change.current
          )
        )
      }
      change.current
    }
  }

  override fun emit() {
    visitProperties {
      if (it.mutableValueFlow != null) {
        val value = it.mutableValueFlow.value
        mutableValueChangeFlow.tryEmit(
          State.Change(
            path = it.path,
            source = this,
            current = value,
            previous = value
          )
        )
      }
    }
  }

  override fun visitProperties(
    visitor: (property: State.Property<*>) -> Unit
  ) {
    children.visitProperties(visitor)
  }

  private fun Map<String, State.Property<*>>.findProperty(
    path: List<String>,
    level: Int
  ): State.Property<*> {
    val property = this[path[level]]
    require(property != null) { "No such property, path: $path" }
    return (
        if (level == (path.size - 1)) property
        else property.children.findProperty(path, level + 1)
    )
  }

  private fun Map<String, State.Property<*>>.visitProperties(
    visitor: (property: State.Property<*>) -> Unit
  ) {
    values.forEach {
      visitor(it)
      it.children.visitProperties(visitor)
    }
  }

}
