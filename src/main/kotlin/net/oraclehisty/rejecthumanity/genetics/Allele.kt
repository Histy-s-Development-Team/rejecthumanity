package net.oraclehisty.rejecthumanity.genetics

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.RandomSource
import net.oraclehisty.rejecthumanity.helpers.FloatRange
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

/** One copy of a gene. Continuous genes carry a number, discrete genes carry a named variant. */
sealed interface Allele {
    data class Continuous(val value: Float) : Allele {
        override fun toString() = "%.3f".format(value)
    }

    data class Discrete(val id: String) : Allele {
        override fun toString() = id
    }

    /** The trait this allele shows when it is the only thing expressed (homozygous / epistasis override). */
    fun asTrait(range: FloatRange?): Trait = when (this) {
        is Continuous -> Trait.Value(value, range?.normalize(value) ?: value)
        is Discrete -> Trait.Variant(id, id)
    }

    companion object {
        /** JSON/NBT: a number is continuous, a string is discrete. */
        val CODEC: Codec<Allele> = Codec.either(Codec.FLOAT, Codec.STRING).xmap(
            { either -> either.map<Allele>({ Continuous(it) }, { Discrete(it) }) },
            { allele ->
                when (allele) {
                    is Continuous -> Either.left<Float, String>(allele.value)
                    is Discrete -> Either.right<Float, String>(allele.id)
                }
            },
        )

        fun parse(text: String): Allele = text.toFloatOrNull()?.let(::Continuous) ?: Discrete(text)
    }
}

/** The pair of alleles an individual carries for one gene. */
data class Genotype(val first: Allele, val second: Allele) {
    fun pick(random: RandomSource) = if (random.nextBoolean()) first else second

    override fun toString() = "$first/$second"

    companion object {
        val CODEC: Codec<Genotype> = Allele.CODEC.listOf().comapFlatMap(
            { list ->
                if (list.size == 2) DataResult.success(Genotype(list[0], list[1]))
                else DataResult.error { "Genotype needs exactly 2 alleles, got ${list.size}" }
            },
            { listOf(it.first, it.second) },
        )
    }
}

/**
 * An entry in a default allele array: `{"value": 0.4, "weight": 2}` or `{"id": "green", "dominance": 2, "weight": 3}`.
 * Weight 0 makes an allele known (valid, has a dominance) without ever being rolled at random.
 */
data class AlleleSpec(val allele: Allele, val weight: Int = 1, val dominance: Int = 0) {
    private data class Raw(val value: Float?, val id: String?, val weight: Int, val dominance: Int)

    companion object {
        private val RAW: Codec<Raw> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.FLOAT.nullableFieldOf("value", Raw::value),
                Codec.STRING.nullableFieldOf("id", Raw::id),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Raw::weight),
                Codec.INT.optionalFieldOf("dominance", 0).forGetter(Raw::dominance),
            ).apply(i) { value, id, weight, dominance -> Raw(value.orNull(), id.orNull(), weight, dominance) }
        }

        val CODEC: Codec<AlleleSpec> = RAW.comapFlatMap(
            { raw ->
                when {
                    raw.value != null -> DataResult.success(AlleleSpec(Allele.Continuous(raw.value), raw.weight, raw.dominance))
                    raw.id != null -> DataResult.success(AlleleSpec(Allele.Discrete(raw.id), raw.weight, raw.dominance))
                    else -> DataResult.error { "Allele entry needs either \"value\" or \"id\"" }
                }
            },
            { spec ->
                val allele = spec.allele
                Raw((allele as? Allele.Continuous)?.value, (allele as? Allele.Discrete)?.id, spec.weight, spec.dominance)
            },
        )
    }
}

/** A fixed default pair, for genes where free allele combination is invalid (e.g. sex: XX/XY but never YY). */
data class GenotypeSpec(val pair: Genotype, val weight: Int = 1) {
    companion object {
        val CODEC: Codec<GenotypeSpec> = RecordCodecBuilder.create { i ->
            i.group(
                Genotype.CODEC.fieldOf("pair").forGetter(GenotypeSpec::pair),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(GenotypeSpec::weight),
            ).apply(i, ::GenotypeSpec)
        }
    }
}

fun <T> List<T>.weightedPick(random: RandomSource, weight: (T) -> Int): T? {
    val total = sumOf { weight(it).coerceAtLeast(0) }
    if (total <= 0) return null
    var roll = random.nextInt(total)
    for (entry in this) {
        roll -= weight(entry).coerceAtLeast(0)
        if (roll < 0) return entry
    }
    return null
}
