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
    fun evaluate(phenotype: Phenotype) = evaluate(phenotype, 1f)

    /** [neutral] is returned when the rule doesn't apply: 1 for multipliers, 0 for offsets such as rotations. */
    fun evaluate(phenotype: Phenotype, neutral: Float): Float {
        if (whenCondition != null && !whenCondition.test(phenotype)) return neutral
        if (gene == null) return if (range == FloatRange.UNIT) factor else range.min * factor
        val value = phenotype.normalized(gene) ?: return neutral
        return range.lerp(value) * factor
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
 * - [palette] may contain `{namespace:gene}` placeholders, so another gene can choose the palette
 *   (e.g. `"skin_{rejecthumanity:skin_undertone}"`)
 * - [source] "secondary" reads the non-dominant allele; with [distinct], a homozygous secondary picks a different
 *   palette entry (stable per individual)
 * - [source] "random" needs no gene: it picks a palette entry per individual (cosmetic choices such as hair dye)
 * - [darken] 0..1 shades the result
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
        val name = fillPlaceholders(palette ?: return null, phenotype, seed)?.first ?: return null
        val pal = palettes[name] ?: return null
        if (source == "random") return randomEntry(pal, seed xor name.hashCode().toLong())
        val trait = phenotype[gene ?: return null] ?: return null
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
        val (path, values) = fillPlaceholders(texture, phenotype, seed) ?: return null
        if (values.any { it in skipValues }) return null
        return ResourceLocation.tryParse(path)
    }

    companion object {
        val CODEC: Codec<Layer> =RecordCodecBuilder.create { i ->
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
 * Shows/hides, scales and rotates model bones. `"bone"` is one name or a list.
 * - [scale] applies to the [axes] given (so a gene can lengthen ears outward without thickening them)
 * - [rotate] is an angle in degrees added to the bind pose on [rotateAxes], e.g. from a size gene
 * - [jiggle] is a bounce amplitude in degrees on [rotateAxes] (a number, or a gene rule so it grows with e.g. size),
 *   driven by the entity's spring physics; [jiggleChannel] picks which spring, so paired parts move independently
 * - [state] limits the rule to a moment in the entity's life: "aggressive", "moving", "idle", "baby" or "sleeping"
 *   (put it after the rules it should override, e.g. ears pinned back while fighting)
 */
data class BoneRule(
    val bones: List<String>,
    val visibleIf: Condition?,
    val scale: ScaleRule?,
    val axes: String,
    val rotate: ScaleRule?,
    val rotateAxes: String,
    val jiggle: ScaleRule?,
    val jiggleChannel: Int,
    val state: String?,
) {
    companion object {
        private val NAMES: Codec<List<String>> = Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(
            { either -> either.map({ listOf(it) }, { it }) },
            { names -> if (names.size == 1) Either.left<String, List<String>>(names[0]) else Either.right<String, List<String>>(names) },
        )

        /** A plain number, or a full gene-driven rule. */
        private val AMOUNT: Codec<ScaleRule> = Codec.either(Codec.FLOAT, ScaleRule.CODEC).xmap(
            { either -> either.map({ ScaleRule(null, FloatRange.UNIT, it, null) }, { it }) },
            { Either.right<Float, ScaleRule>(it) },
        )

        val CODEC: Codec<BoneRule> = RecordCodecBuilder.create { i ->
            i.group(
                NAMES.fieldOf("bone").forGetter(BoneRule::bones),
                Condition.CODEC.nullableFieldOf("visible_if", BoneRule::visibleIf),
                ScaleRule.CODEC.nullableFieldOf("scale", BoneRule::scale),
                Codec.STRING.optionalFieldOf("axes", "xyz").forGetter(BoneRule::axes),
                ScaleRule.CODEC.nullableFieldOf("rotate", BoneRule::rotate),
                Codec.STRING.optionalFieldOf("rotate_axes", "x").forGetter(BoneRule::rotateAxes),
                AMOUNT.nullableFieldOf("jiggle", BoneRule::jiggle),
                Codec.INT.optionalFieldOf("jiggle_channel", 0).forGetter(BoneRule::jiggleChannel),
                Codec.STRING.nullableFieldOf("state", BoneRule::state),
            ).apply(i) { bones, visibleIf, scale, axes, rotate, rotateAxes, jiggle, jiggleChannel, state ->
                BoneRule(bones, visibleIf.orNull(), scale.orNull(), axes, rotate.orNull(), rotateAxes, jiggle.orNull(), jiggleChannel, state.orNull())
            }
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

private val PLACEHOLDER = Regex("\\{([^}]+)}")

/**
 * Fills `{namespace:gene}` with the gene's expressed variant and `{random:N}` with a per-individual number 0..N-1
 * (salted by [text], so different layers roll independently). Returns the filled text plus every substituted value,
 * or null when a referenced gene isn't expressed.
 */
fun fillPlaceholders(text: String, phenotype: Phenotype, seed: Long): Pair<String, List<String>>? {
    val values = mutableListOf<String>()
    var missing = false
    val filled = PLACEHOLDER.replace(text) { match ->
        val key = match.groupValues[1]
        val value = if (key.startsWith("random:")) {
            val count = key.removePrefix("random:").toIntOrNull()?.coerceAtLeast(1) ?: 1
            Math.floorMod(seed xor text.hashCode().toLong(), count.toLong()).toString()
        } else {
            ResourceLocation.tryParse(key)?.let(phenotype::variant)?.primary
        }
        if (value == null) missing = true
        value.orEmpty().also(values::add)
    }
    return if (missing) null else filled to values
}

fun lerpRgb(from: Int, to: Int, t: Float): Int {
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * t).toInt().coerceIn(0, 255)
    }
    return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
