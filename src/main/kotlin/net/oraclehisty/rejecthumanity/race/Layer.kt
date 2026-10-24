package net.oraclehisty.rejecthumanity.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.genetics.Condition
import net.oraclehisty.rejecthumanity.genetics.Phenotype
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

/**
 * One texture drawn over the model. [texture] may contain `{namespace:gene}` (replaced by the gene's expressed
 * variant) and `{random:N}` (a per-individual number 0..N-1). Layers with a missing gene or a value in
 * [skipValues] are not drawn. [hideOnBlink] layers (eyes) disappear for a few ticks every few seconds;
 * [adultOnly] layers (e.g. beards) are not drawn on babies or teens.
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
