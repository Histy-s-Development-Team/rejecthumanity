package net.oraclehisty.rejecthumanity.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.StringRepresentable
import net.oraclehisty.rejecthumanity.genetics.Phenotype
import net.oraclehisty.rejecthumanity.genetics.Trait
import net.oraclehisty.rejecthumanity.helpers.HEX_COLOR_CODEC
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

/** Which value of a tint's gene picks the color. */
enum class TintSource(private val serialized: String) : StringRepresentable {
    /** The expressed (dominant) allele. */
    PRIMARY("primary"),

    /** The other allele of the pair, e.g. the second eye of a heterochromatic individual. */
    SECONDARY("secondary"),

    /** No gene: a palette entry picked per individual (cosmetic choices such as hair dye). */
    RANDOM("random");

    override fun getSerializedName() = serialized

    companion object {
        val CODEC: Codec<TintSource> = StringRepresentable.fromEnum(::values)
    }
}

/**
 * Where a layer's color comes from: a fixed [color], or [gene] looked up in a named [palette].
 * - [palette] may contain `{namespace:gene}` placeholders, so another gene can choose the palette
 *   (e.g. `"skin_{rejecthumanity:skin_undertone}"`)
 * - with [distinct], a homozygous [TintSource.SECONDARY] picks a different palette entry (stable per individual)
 * - [darken] 0..1 shades the result
 */
data class Tint(
    val color: Int?,
    val gene: ResourceLocation?,
    val palette: String?,
    val source: TintSource,
    val distinct: Boolean,
    val darken: Float,
) {
    fun resolve(phenotype: Phenotype, palettes: Map<String, Palette>, seed: Long): Int {
        val rgb = color ?: fromPalette(phenotype, palettes, seed) ?: return 0xFFFFFF
        return if (darken > 0f) lerpRgb(rgb, 0x000000, darken) else rgb
    }

    private fun fromPalette(phenotype: Phenotype, palettes: Map<String, Palette>, seed: Long): Int? {
        val name = fillPlaceholders(palette ?: return null, phenotype, seed)?.first ?: return null
        val pal = palettes[name] ?: return null
        if (source == TintSource.RANDOM) return randomEntry(pal, seed xor name.hashCode().toLong())
        return when (val trait = phenotype[gene ?: return null] ?: return null) {
            is Trait.Value -> (pal as? Palette.Gradient)?.sample(trait.normalized)
            is Trait.Variant -> {
                val colors = (pal as? Palette.Mapped)?.colors ?: return null
                var key = if (source == TintSource.SECONDARY) trait.secondary else trait.primary
                if (distinct && source == TintSource.SECONDARY && key == trait.primary) {
                    val others = colors.keys.filter { it != trait.primary }.sorted()
                    if (others.isNotEmpty()) key = others[Math.floorMod(seed xor gene.hashCode().toLong(), others.size.toLong()).toInt()]
                }
                colors[key]
            }
        }
    }

    private fun randomEntry(palette: Palette, seed: Long): Int? = when (palette) {
        is Palette.Mapped -> palette.colors.keys.sorted().takeIf { it.isNotEmpty() }
            ?.let { keys -> palette.colors[keys[Math.floorMod(seed, keys.size.toLong()).toInt()]] }
        is Palette.Gradient -> palette.sample(Math.floorMod(seed, 1000L) / 999f)
    }

    companion object {
        val CODEC: Codec<Tint> = RecordCodecBuilder.create { i ->
            i.group(
                HEX_COLOR_CODEC.nullableFieldOf("color", Tint::color),
                ResourceLocation.CODEC.nullableFieldOf("gene", Tint::gene),
                Codec.STRING.nullableFieldOf("palette", Tint::palette),
                TintSource.CODEC.optionalFieldOf("source", TintSource.PRIMARY).forGetter(Tint::source),
                Codec.BOOL.optionalFieldOf("distinct", false).forGetter(Tint::distinct),
                Codec.FLOAT.optionalFieldOf("darken", 0f).forGetter(Tint::darken),
            ).apply(i) { color, gene, palette, source, distinct, darken ->
                Tint(color.orNull(), gene.orNull(), palette.orNull(), source, distinct, darken)
            }
        }
    }
}
