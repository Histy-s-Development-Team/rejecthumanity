package net.oraclehisty.rejecthumanity.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.genetics.Condition
import net.oraclehisty.rejecthumanity.genetics.Phenotype
import net.oraclehisty.rejecthumanity.helpers.FloatRange
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

/**
 * A number derived from a gene: the gene's normalized value mapped into [range], times [factor].
 * Without a [gene] it is just [factor] (a constant). When [whenCondition] fails the rule doesn't apply.
 */
data class ScaleRule(val gene: ResourceLocation?, val range: FloatRange, val factor: Float, val whenCondition: Condition?) {
    /** As a multiplier: 1 when the rule doesn't apply. */
    fun evaluate(phenotype: Phenotype) = evaluate(phenotype, 1f)

    /** [neutral] is returned when the rule doesn't apply: 1 for multipliers, 0 for offsets such as rotations. */
    fun evaluate(phenotype: Phenotype, neutral: Float): Float {
        if (whenCondition != null && !whenCondition.test(phenotype)) return neutral
        if (gene == null) return factor
        val value = phenotype.normalized(gene) ?: return neutral
        return range.lerp(value) * factor
    }

    companion object {
        fun constant(value: Float) = ScaleRule(null, FloatRange.UNIT, value, null)

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
