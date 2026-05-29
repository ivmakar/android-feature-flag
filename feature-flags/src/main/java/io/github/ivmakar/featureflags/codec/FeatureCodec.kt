package io.github.ivmakar.featureflags.codec

import io.github.ivmakar.featureflags.api.BoolFeature
import io.github.ivmakar.featureflags.api.EnumFeature
import io.github.ivmakar.featureflags.api.Feature
import io.github.ivmakar.featureflags.api.IntFeature
import io.github.ivmakar.featureflags.api.StringFeature

// Sources speak raw String?; the codec is the only place that knows how each Feature subtype
// serialises. decode returns null for an unparseable raw value so the resolver falls through to
// the next source instead of surfacing garbage (e.g. a renamed enum case, a non-numeric Int).
internal object FeatureCodec {

    fun <T : Any> encode(feature: Feature<T>, value: T): String = when (feature) {
        is BoolFeature -> (value as Boolean).toString()
        is IntFeature -> (value as Int).toString()
        is StringFeature -> value as String
        is EnumFeature<*> -> (value as Enum<*>).name
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> decode(feature: Feature<T>, raw: String): T? = when (feature) {
        is BoolFeature -> when (raw) {
            "true" -> true as T
            "false" -> false as T
            else -> null
        }
        is IntFeature -> raw.toIntOrNull() as T?
        is StringFeature -> raw as T
        is EnumFeature<*> -> decodeEnum(feature, raw) as T?
    }

    private fun <E : Enum<E>> decodeEnum(feature: EnumFeature<E>, raw: String): E? =
        feature.klass.java.enumConstants?.firstOrNull { it.name == raw }
}
