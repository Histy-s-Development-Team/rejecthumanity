package net.oraclehisty.rejecthumanity.race

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.genetics.Condition
import net.oraclehisty.rejecthumanity.genetics.Phenotype
import net.oraclehisty.rejecthumanity.genetics.Trait
import net.oraclehisty.rejecthumanity.helpers.FloatRange
import net.oraclehisty.rejecthumanity.helpers.HEX_COLOR_CODEC
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

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

/**
 * Multiplies a value from a gene. With [gene]: the gene's normalized value is mapped into [range]; without: just [factor].
 * If [whenCondition] fails the rule contributes 1 (no change).
 */
data class ScaleRule(val gene: ResourceLocation?, val range: FloatRange, val factor: Float, val whenCondition: Condition?) {
    fun evaluate(phenotype: Phenotype): Float {
        if (whenCondition != null && !whenCondition.test(phenotype)) return 1f
        val fromGene = gene?.let { id -> phenotype.normalized(id)?.let(range::lerp) } ?: 1f
        return fromGene * factor
    }

    companion object {
        val CODEC: Codec<ScaleRule> = RecordCodecBuilder.create { i ->
            i.group(
                ResourceLocation.CODEC.nullableFieldOf("gene", ScaleRule::gene),
                FloatRange.CODEC.optionalFieldOf("range", FloatRange.UNIT).forGetter(ScaleRule::range),
                Codec.FLOAT.optionalFieldOf("factor", 1f).forGetter(ScaleRule::factor),
                Condition.CODEC.nullableFieldOf("when", ScaleRule::whenCondition),
            ).apply(i) { gene, range, factor, whenCondition -> ScaleRule(gene.orNull(), range, factor, whenCondition.orNull()) }
        }
    }
}

/**
 * Where a layer's color comes from: a fixed [color], or [gene] looked up in a named [palette].
 * [source] "secondary" reads the non-dominant allele; with [distinct], a homozygous secondary picks a different
 * palette entry (stable per individual). [darken] 0..1 shades the result.
 */
data class Tint(
    val color: Int?,
    val gene: ResourceLocation?,
    val palette: String?,
    val source: String,
    val distinct: Boolean,
    val darken: Float,
) {
    fun resolve(phenotype: Phenotype, palettes: Map<String, Palette>, seed: Long): Int {
        val rgb = color ?: fromGene(phenotype, palettes, seed) ?: return 0xFFFFFF
        return if (darken > 0f) lerpRgb(rgb, 0x000000, darken) else rgb
    }

    private fun fromGene(phenotype: Phenotype, palettes: Map<String, Palette>, seed: Long): Int? {
        val trait = phenotype[gene ?: return null] ?: return null
        val pal = palettes[palette ?: return null] ?: return null
        return when (trait) {
            is Trait.Value -> (pal as? Palette.Gradient)?.sample(trait.normalized)
            is Trait.Variant -> {
                val colors = (pal as? Palette.Mapped)?.colors ?: return null
                var key = if (source == "secondary") trait.secondary else trait.primary
                if (distinct && source == "secondary" && key == trait.primary) {
                    val others = colors.keys.filter { it != trait.primary }.sorted()
                    if (others.isNotEmpty()) key = others[Math.floorMod(seed xor gene.hashCode().toLong(), others.size.toLong()).toInt()]
                }
                colors[key]
            }
        }
    }

    companion object {
        val CODEC: Codec<Tint> = RecordCodecBuilder.create { i ->
            i.group(
                HEX_COLOR_CODEC.nullableFieldOf("color", Tint::color),
                ResourceLocation.CODEC.nullableFieldOf("gene", Tint::gene),
                Codec.STRING.nullableFieldOf("palette", Tint::palette),
                Codec.STRING.optionalFieldOf("source", "primary").forGetter(Tint::source),
                Codec.BOOL.optionalFieldOf("distinct", false).forGetter(Tint::distinct),
                Codec.FLOAT.optionalFieldOf("darken", 0f).forGetter(Tint::darken),
            ).apply(i) { color, gene, palette, source, distinct, darken -> Tint(color.orNull(), gene.orNull(), palette.orNull(), source, distinct, darken) }
        }
    }
}

/**
 * One texture drawn over the model. [texture] may contain `{namespace:gene}` (replaced by the gene's expressed
 * variant) and `{random:N}` (a per-individual number 0..N-1). Layers with a missing gene or a value in
 * [skipValues] are not drawn. [hideOnBlink] layers (eyes) disappear for a few ticks every few seconds;
 * [adultOnly] layers (e.g. beards) are not drawn on babies.
 */
data class Layer(
    val texture: String,
    val tint: Tint?,
    val alpha: ScaleRule?,
    val emissive: Boolean,
    val whenCondition: Condition?,
    val skipValues: List<String>,
    val hideOnBlink: Boolean,
    val adultOnly: Boolean,
) {
    fun resolveTexture(phenotype: Phenotype, seed: Long): ResourceLocation? {
        var drawable = true
        val path = PLACEHOLDER.replace(texture) { match ->
            val key = match.groupValues[1]
            if (key.startsWith("random:")) {
                val count = key.removePrefix("random:").toIntOrNull()?.coerceAtLeast(1) ?: 1
                Math.floorMod(seed xor texture.hashCode().toLong(), count.toLong()).toString()
            } else {
                val value = ResourceLocation.tryParse(key)?.let(phenotype::variant)?.primary
                if (value == null || value in skipValues) drawable = false
                value.orEmpty()
            }
        }
        return if (drawable) ResourceLocation.tryParse(path) else null
    }

    companion object {
        private val PLACEHOLDER = Regex("\\{([^}]+)}")

        val CODEC: Codec<Layer> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.STRING.fieldOf("texture").forGetter(Layer::texture),
                Tint.CODEC.nullableFieldOf("tint", Layer::tint),
                ScaleRule.CODEC.nullableFieldOf("alpha", Layer::alpha),
                Codec.BOOL.optionalFieldOf("emissive", false).forGetter(Layer::emissive),
                Condition.CODEC.nullableFieldOf("when", Layer::whenCondition),
                Codec.STRING.listOf().optionalFieldOf("skip_values", emptyList()).forGetter(Layer::skipValues),
                Codec.BOOL.optionalFieldOf("hide_on_blink", false).forGetter(Layer::hideOnBlink),
                Codec.BOOL.optionalFieldOf("adult_only", false).forGetter(Layer::adultOnly),
            ).apply(i) { texture, tint, alpha, emissive, whenCondition, skipValues, hideOnBlink, adultOnly ->
                Layer(texture, tint.orNull(), alpha.orNull(), emissive, whenCondition.orNull(), skipValues, hideOnBlink, adultOnly)
            }
        }
    }
}

/**
 * Shows/hides and scales model bones. `"bone"` is one name or a list. [axes] picks which of x/y/z the scale
 * applies to, so a gene can e.g. lengthen ears outward without thickening them.
 */
data class BoneRule(val bones: List<String>, val visibleIf: Condition?, val scale: ScaleRule?, val axes: String) {
    companion object {
        private val NAMES: Codec<List<String>> = Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(
            { either -> either.map({ listOf(it) }, { it }) },
            { names -> if (names.size == 1) Either.left<String, List<String>>(names[0]) else Either.right<String, List<String>>(names) },
        )

        val CODEC: Codec<BoneRule> = RecordCodecBuilder.create { i ->
            i.group(
                NAMES.fieldOf("bone").forGetter(BoneRule::bones),
                Condition.CODEC.nullableFieldOf("visible_if", BoneRule::visibleIf),
                ScaleRule.CODEC.nullableFieldOf("scale", BoneRule::scale),
                Codec.STRING.optionalFieldOf("axes", "xyz").forGetter(BoneRule::axes),
            ).apply(i) { bones, visibleIf, scale, axes -> BoneRule(bones, visibleIf.orNull(), scale.orNull(), axes) }
        }
    }
}

/**
 * What changes while an individual is a baby: an optional separate [model] (same bone names, so animations and
 * adult bone rules still apply where the bones exist) and extra [bones] rules applied after the adult ones.
 */
data class BabyAppearance(val model: ResourceLocation?, val bones: List<BoneRule>) {
    companion object {
        val NONE = BabyAppearance(null, emptyList())
        val CODEC: Codec<BabyAppearance> = RecordCodecBuilder.create { i ->
            i.group(
                ResourceLocation.CODEC.nullableFieldOf("model", BabyAppearance::model),
                BoneRule.CODEC.listOf().optionalFieldOf("bones", emptyList()).forGetter(BabyAppearance::bones),
            ).apply(i) { model, bones -> BabyAppearance(model.orNull(), bones) }
        }
    }
}

/**
 * How a race turns a phenotype into visuals. [model] names `geo/entity/<path>.geo.json` and
 * `animations/entity/<path>.animation.json`. [scale] rules multiply together into the entity's size.
 */
data class Appearance(
    val model: ResourceLocation,
    val base: Layer,
    val layers: List<Layer>,
    val bones: List<BoneRule>,
    val scale: List<ScaleRule>,
    val palettes: Map<String, Palette>,
    val baby: BabyAppearance,
) {
    fun modelFor(isBaby: Boolean) = if (isBaby) baby.model ?: model else model

    fun bonesFor(isBaby: Boolean) = if (isBaby) bones + baby.bones else bones

    fun scaleFor(phenotype: Phenotype) = scale.fold(1f) { acc, rule -> acc * rule.evaluate(phenotype) }

    fun tint(tint: Tint?, phenotype: Phenotype, seed: Long) = tint?.resolve(phenotype, palettes, seed) ?: 0xFFFFFF

    companion object {
        val CODEC: Codec<Appearance> = RecordCodecBuilder.create { i ->
            i.group(
                ResourceLocation.CODEC.fieldOf("model").forGetter(Appearance::model),
                Layer.CODEC.fieldOf("base").forGetter(Appearance::base),
                Layer.CODEC.listOf().optionalFieldOf("layers", emptyList()).forGetter(Appearance::layers),
                BoneRule.CODEC.listOf().optionalFieldOf("bones", emptyList()).forGetter(Appearance::bones),
                ScaleRule.CODEC.listOf().optionalFieldOf("scale", emptyList()).forGetter(Appearance::scale),
                Codec.unboundedMap(Codec.STRING, Palette.CODEC).optionalFieldOf("palettes", emptyMap()).forGetter(Appearance::palettes),
                BabyAppearance.CODEC.optionalFieldOf("baby", BabyAppearance.NONE).forGetter(Appearance::baby),
            ).apply(i, ::Appearance)
        }
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
