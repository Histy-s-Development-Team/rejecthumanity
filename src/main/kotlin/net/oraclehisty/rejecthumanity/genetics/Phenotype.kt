package net.oraclehisty.rejecthumanity.genetics

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

/** What a gene looks like once expressed. */
sealed interface Trait {
    /** [normalized] is [value] mapped into 0..1 using the gene's range; appearance rules work on it. */
    data class Value(val value: Float, val normalized: Float) : Trait {
        override fun toString() = "%.3f".format(value)
    }

    /** [primary] is the dominant (shown) allele, [secondary] the other one of the pair (same as primary when homozygous). */
    data class Variant(val primary: String, val secondary: String) : Trait {
        override fun toString() = if (primary == secondary) primary else "$primary ($secondary)"
    }
}

class Phenotype(val traits: Map<ResourceLocation, Trait>, val hidden: Set<ResourceLocation> = emptySet()) {
    operator fun get(gene: ResourceLocation) = traits[gene]
    fun value(gene: ResourceLocation) = (traits[gene] as? Trait.Value)?.value
    fun normalized(gene: ResourceLocation) = (traits[gene] as? Trait.Value)?.normalized
    fun variant(gene: ResourceLocation) = traits[gene] as? Trait.Variant

    companion object {
        val EMPTY = Phenotype(emptyMap())
    }
}

/**
 * A test against an expressed phenotype, shared by gene `expressed_if` and race appearance rules.
 * `{"gene": "...", "is": "y"}`, `{"gene": "...", "is_not": "none"}`, `{"gene": "...", "min": 0.5}`.
 * A gene that is not expressed never passes.
 */
data class Condition(
    val gene: ResourceLocation,
    val isValue: String?,
    val isNot: String?,
    val min: Float?,
    val max: Float?,
) {
    fun test(phenotype: Phenotype): Boolean = when (val trait = phenotype[gene]) {
        is Trait.Variant -> (isValue == null || trait.primary == isValue) && (isNot == null || trait.primary != isNot)
        is Trait.Value -> (min == null || trait.value >= min) && (max == null || trait.value <= max)
        null -> false
    }

    companion object {
        val CODEC: Codec<Condition> = RecordCodecBuilder.create { i ->
            i.group(
                ResourceLocation.CODEC.fieldOf("gene").forGetter(Condition::gene),
                Codec.STRING.nullableFieldOf("is", Condition::isValue),
                Codec.STRING.nullableFieldOf("is_not", Condition::isNot),
                Codec.FLOAT.nullableFieldOf("min", Condition::min),
                Codec.FLOAT.nullableFieldOf("max", Condition::max),
            ).apply(i) { gene, isValue, isNot, min, max -> Condition(gene, isValue.orNull(), isNot.orNull(), min.orNull(), max.orNull()) }
        }
    }
}
