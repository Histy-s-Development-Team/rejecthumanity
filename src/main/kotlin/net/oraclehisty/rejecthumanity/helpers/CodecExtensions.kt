package net.oraclehisty.rejecthumanity.helpers

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

/**
 * Optional record field backed by a nullable Kotlin property. DataFixerUpper can't carry `null` through a
 * DataResult, so the field stays an [Optional] inside the codec; unwrap it with [orNull] in the constructor call.
 */
fun <O, T : Any> Codec<T>.nullableFieldOf(name: String, getter: (O) -> T?): RecordCodecBuilder<O, Optional<T>> =
    optionalFieldOf(name).forGetter { Optional.ofNullable(getter(it)) }

fun <T : Any> Optional<T>.orNull(): T? = orElse(null)

/** "#RRGGBB" (or "RRGGBB") <-> 0xRRGGBB. */
val HEX_COLOR_CODEC: Codec<Int> = Codec.STRING.comapFlatMap(
    { text ->
        text.removePrefix("#").toIntOrNull(16)?.let { DataResult.success(it and 0xFFFFFF) }
            ?: DataResult.error { "Invalid hex color '$text'" }
    },
    { "#%06X".format(it and 0xFFFFFF) },
)

data class FloatRange(val min: Float, val max: Float) {
    fun lerp(t: Float) = min + (max - min) * t
    fun normalize(value: Float) = if (max == min) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)
    fun clamp(value: Float) = value.coerceIn(minOf(min, max), maxOf(min, max))

    companion object {
        val UNIT = FloatRange(0f, 1f)
        val ONE = FloatRange(1f, 1f)
        val CODEC: Codec<FloatRange> = Codec.FLOAT.listOf().comapFlatMap(
            { list ->
                if (list.size == 2) DataResult.success(FloatRange(list[0], list[1]))
                else DataResult.error { "Range needs exactly 2 numbers, got ${list.size}" }
            },
            { listOf(it.min, it.max) },
        )
    }
}
