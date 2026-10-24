package net.oraclehisty.rejecthumanity.genetics

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.oraclehisty.rejecthumanity.helpers.FloatRange
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

/**
 * The default allele array of a gene (`defaults`), optionally replaced by fixed pairs (`default_genotypes`).
 * Races use the same shape to override a gene's pool.
 */
data class AllelePool(val defaults: List<AlleleSpec>, val genotypes: List<GenotypeSpec>) {
    val isEmpty get() = defaults.none { it.weight > 0 } && genotypes.none { it.weight > 0 }

    fun roll(random: RandomSource): Genotype? {
        if (genotypes.isNotEmpty()) return genotypes.weightedPick(random) { it.weight }?.pair
        val first = defaults.weightedPick(random) { it.weight } ?: return null
        val second = defaults.weightedPick(random) { it.weight } ?: return null
        return Genotype(first.allele, second.allele)
    }

    companion object {
        val EMPTY = AllelePool(emptyList(), emptyList())
        val MAP_CODEC: MapCodec<AllelePool> = RecordCodecBuilder.mapCodec { i ->
            i.group(
                AlleleSpec.CODEC.listOf().optionalFieldOf("defaults", emptyList()).forGetter(AllelePool::defaults),
                GenotypeSpec.CODEC.listOf().optionalFieldOf("default_genotypes", emptyList()).forGetter(AllelePool::genotypes),
            ).apply(i, ::AllelePool)
        }
        val CODEC: Codec<AllelePool> = MAP_CODEC.codec()
    }
}

/** When this gene expresses [whenValue], force other genes to the given alleles (e.g. albinism -> no pigment). */
data class Epistasis(val whenValue: String, val set: Map<ResourceLocation, Allele>, val priority: Int) {
    companion object {
        val CODEC: Codec<Epistasis> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.STRING.fieldOf("when").forGetter(Epistasis::whenValue),
                Codec.unboundedMap(ResourceLocation.CODEC, Allele.CODEC).fieldOf("set").forGetter(Epistasis::set),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(Epistasis::priority),
            ).apply(i, ::Epistasis)
        }
    }
}

/** A datapack-defined gene (`data/<ns>/rejecthumanity/gene/<name>.json`). Knows nothing about races. */
sealed interface GeneDefinition {
    val type: String
    val pool: AllelePool
    /** Expressed and inherited, but not meant to be rendered (e.g. magic capacity). */
    val hidden: Boolean
    /** If set and false for the individual, the trait shows [fallback] instead (alleles are still carried). */
    val expressedIf: Condition?
    val fallback: Allele?
    val epistasis: List<Epistasis>

    fun express(genotype: Genotype): Trait
    fun mutate(allele: Allele, random: RandomSource): Allele
    fun accepts(allele: Allele): Boolean
    fun valueRange(): FloatRange? = null

    data class Continuous(
        val range: FloatRange,
        val mutation: Float,
        override val pool: AllelePool,
        override val hidden: Boolean,
        override val expressedIf: Condition?,
        override val fallback: Allele?,
        override val epistasis: List<Epistasis>,
    ) : GeneDefinition {
        override val type get() = "continuous"
        override fun valueRange() = range

        /** Additive: the mean of both copies. */
        override fun express(genotype: Genotype): Trait {
            val value = (valueOf(genotype.first) + valueOf(genotype.second)) / 2f
            return Trait.Value(value, range.normalize(value))
        }

        override fun mutate(allele: Allele, random: RandomSource): Allele {
            if (mutation <= 0f) return allele
            return Allele.Continuous(range.clamp(valueOf(allele) + random.nextGaussian().toFloat() * mutation))
        }

        override fun accepts(allele: Allele) = allele is Allele.Continuous && range.clamp(allele.value) == allele.value

        private fun valueOf(allele: Allele) = (allele as? Allele.Continuous)?.value ?: range.min

        companion object {
            val MAP_CODEC: MapCodec<Continuous> = RecordCodecBuilder.mapCodec { i ->
                i.group(
                    FloatRange.CODEC.optionalFieldOf("range", FloatRange.UNIT).forGetter(Continuous::range),
                    Codec.FLOAT.optionalFieldOf("mutation", 0f).forGetter(Continuous::mutation),
                    AllelePool.MAP_CODEC.forGetter(Continuous::pool),
                    Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Continuous::hidden),
                    Condition.CODEC.nullableFieldOf("expressed_if", Continuous::expressedIf),
                    Allele.CODEC.nullableFieldOf("fallback", Continuous::fallback),
                    Epistasis.CODEC.listOf().optionalFieldOf("epistasis", emptyList()).forGetter(Continuous::epistasis),
                ).apply(i) { range, mutation, pool, hidden, expressedIf, fallback, epistasis -> Continuous(range, mutation, pool, hidden, expressedIf.orNull(), fallback.orNull(), epistasis) }
            }
        }
    }

    data class Discrete(
        val mutationChance: Float,
        override val pool: AllelePool,
        override val hidden: Boolean,
        override val expressedIf: Condition?,
        override val fallback: Allele?,
        override val epistasis: List<Epistasis>,
    ) : GeneDefinition {
        override val type get() = "discrete"

        private val dominance: Map<String, Int> =
            pool.defaults.mapNotNull { spec -> (spec.allele as? Allele.Discrete)?.let { it.id to spec.dominance } }.toMap()

        val knownAlleles: Set<String>
            get() = dominance.keys + pool.genotypes.flatMap { listOf(it.pair.first, it.pair.second) }
                .filterIsInstance<Allele.Discrete>().map { it.id }

        /** Higher dominance wins; on a tie the first copy shows. */
        override fun express(genotype: Genotype): Trait {
            val a = idOf(genotype.first)
            val b = idOf(genotype.second)
            return if ((dominance[b] ?: 0) > (dominance[a] ?: 0)) Trait.Variant(b, a) else Trait.Variant(a, b)
        }

        override fun mutate(allele: Allele, random: RandomSource): Allele {
            if (mutationChance <= 0f || random.nextFloat() >= mutationChance) return allele
            return pool.defaults.weightedPick(random) { it.weight }?.allele ?: allele
        }

        override fun accepts(allele: Allele) = allele is Allele.Discrete && allele.id in knownAlleles

        private fun idOf(allele: Allele) = (allele as? Allele.Discrete)?.id ?: allele.toString()

        companion object {
            val MAP_CODEC: MapCodec<Discrete> = RecordCodecBuilder.mapCodec { i ->
                i.group(
                    Codec.FLOAT.optionalFieldOf("mutation_chance", 0f).forGetter(Discrete::mutationChance),
                    AllelePool.MAP_CODEC.forGetter(Discrete::pool),
                    Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Discrete::hidden),
                    Condition.CODEC.nullableFieldOf("expressed_if", Discrete::expressedIf),
                    Allele.CODEC.nullableFieldOf("fallback", Discrete::fallback),
                    Epistasis.CODEC.listOf().optionalFieldOf("epistasis", emptyList()).forGetter(Discrete::epistasis),
                ).apply(i) { mutationChance, pool, hidden, expressedIf, fallback, epistasis -> Discrete(mutationChance, pool, hidden, expressedIf.orNull(), fallback.orNull(), epistasis) }
            }
        }
    }

    companion object {
        private val TYPES: Map<String, MapCodec<out GeneDefinition>> = mapOf(
            "continuous" to Continuous.MAP_CODEC,
            "discrete" to Discrete.MAP_CODEC,
        )

        val CODEC: Codec<GeneDefinition> = Codec.STRING
            .validate { type ->
                if (type in TYPES) DataResult.success(type)
                else DataResult.error { "Unknown gene type '$type' (expected one of ${TYPES.keys})" }
            }
            .dispatch("type", GeneDefinition::type) { TYPES.getValue(it) }
    }
}
