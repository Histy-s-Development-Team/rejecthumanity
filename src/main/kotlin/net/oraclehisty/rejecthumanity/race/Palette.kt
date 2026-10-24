package net.oraclehisty.rejecthumanity.race

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import net.oraclehisty.rejecthumanity.helpers.HEX_COLOR_CODEC

/** Named colors for a race: a gradient sampled by a continuous gene, or a map keyed by a discrete gene's value. */
sealed interface Palette {
    data class Gradient(val stops: List<Int>) : Palette {
        fun sample(t: Float): Int {
            if (stops.isEmpty()) return 0xFFFFFF
            if (stops.size == 1) return stops[0]
            val scaled = t.coerceIn(0f, 1f) * (stops.size - 1)
            val index = scaled.toInt().coerceAtMost(stops.size - 2)
            return lerpRgb(stops[index], stops[index + 1], scaled - index)
        }
    }

    data class Mapped(val colors: Map<String, Int>) : Palette

    companion object {
        /** A JSON list of colors is a gradient; an object of name -> color is a mapped palette. */
        val CODEC: Codec<Palette> = Codec.either(HEX_COLOR_CODEC.listOf(), Codec.unboundedMap(Codec.STRING, HEX_COLOR_CODEC)).xmap(
            { either -> either.map<Palette>(::Gradient, ::Mapped) },
            { palette ->
                when (palette) {
                    is Gradient -> Either.left<List<Int>, Map<String, Int>>(palette.stops)
                    is Mapped -> Either.right<List<Int>, Map<String, Int>>(palette.colors)
                }
            },
        )
    }
}

fun lerpRgb(from: Int, to: Int, t: Float): Int {
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * t).toInt().coerceIn(0, 255)
    }
    return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
