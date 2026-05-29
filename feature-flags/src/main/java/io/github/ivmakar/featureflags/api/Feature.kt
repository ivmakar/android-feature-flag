package io.github.ivmakar.featureflags.api

import kotlin.reflect.KClass

sealed interface Feature<T : Any> {
    val key: String
    val default: T
    val description: String get() = key
}

abstract class BoolFeature(
    override val key: String,
    override val default: Boolean,
    override val description: String = key,
) : Feature<Boolean>

abstract class EnumFeature<E : Enum<E>>(
    override val key: String,
    override val default: E,
    val klass: KClass<E>,
    override val description: String = key,
) : Feature<E>

abstract class StringFeature(
    override val key: String,
    override val default: String,
    override val description: String = key,
) : Feature<String>

abstract class IntFeature(
    override val key: String,
    override val default: Int,
    override val description: String = key,
) : Feature<Int>
