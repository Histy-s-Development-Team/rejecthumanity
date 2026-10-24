package net.oraclehisty.rejecthumanity.genetics

import com.mojang.serialization.Codec
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource

/** Looks a gene up by id; usually backed by the synced datapack registry. */
typealias GeneLookup = (ResourceLocation) -> GeneDefinition?

/** Which genes a species carries, each with an optional pool override (empty = use the gene's own defaults). */
typealias GenePlan = Map<ResourceLocation, AllelePool>

/** Every genotype an individual carries. Immutable: changes produce a new genome. */
data class Genome(val genotypes: Map<ResourceLocation, Genotype>) {
    val isEmpty get() = genotypes.isEmpty()

    operator fun get(gene: ResourceLocation) = genotypes[gene]
    fun with(gene: ResourceLocation, genotype: Genotype) = Genome(genotypes + (gene to genotype))

    /** Adds any planned gene this genome lacks (e.g. a datapack added a gene after the mob was saved). */
    fun completedWith(plan: GenePlan, lookup: GeneLookup, random: RandomSource): Genome {
        val missing = plan.keys - genotypes.keys
        if (missing.isEmpty()) return this
        return Genome(genotypes + missing.mapNotNull { id -> roll(id, plan, lookup, random)?.let { id to it } })
    }

    /**
     * 1. Each gene expresses on its own (dominance / additive mean).
     * 2. `expressed_if` swaps traits whose condition fails for their fallback (judged on step 1).
     * 3. Epistasis rules override other genes, lowest priority first, ties by gene id.
     * Genes unknown to [lookup] are kept in the genome but not expressed.
     */
    fun express(lookup: GeneLookup): Phenotype {
        val defs = genotypes.keys.mapNotNull { id -> lookup(id)?.let { id to it } }.toMap()
        val traits = defs.mapValuesTo(LinkedHashMap()) { (id, def) -> def.express(genotypes.getValue(id)) }

        val raw = Phenotype(traits.toMap())
        for ((id, def) in defs) {
            val condition = def.expressedIf ?: continue
            if (condition.test(raw)) continue
            val fallback = def.fallback
            if (fallback == null) traits.remove(id) else traits[id] = fallback.asTrait(def.valueRange())
        }

        defs.flatMap { (id, def) -> def.epistasis.map { id to it } }
            .sortedWith(compareBy({ it.second.priority }, { it.first.toString() }))
            .forEach { (id, rule) ->
                if ((traits[id] as? Trait.Variant)?.primary != rule.whenValue) return@forEach
                rule.set.forEach { (target, allele) -> traits[target] = allele.asTrait(lookup(target)?.valueRange()) }
            }

        return Phenotype(traits, defs.filterValues { it.hidden }.keys)
    }

    companion object {
        val EMPTY = Genome(emptyMap())

        val CODEC: Codec<Genome> = Codec.unboundedMap(ResourceLocation.CODEC, Genotype.CODEC).xmap(::Genome, Genome::genotypes)
        val STREAM_CODEC: StreamCodec<ByteBuf, Genome> = ByteBufCodecs.fromCodec(CODEC)

        fun random(plan: GenePlan, lookup: GeneLookup, random: RandomSource) =
            Genome(plan.keys.mapNotNull { id -> roll(id, plan, lookup, random)?.let { id to it } }.toMap())

        /** Each parent passes one random copy of every gene, which may then mutate. Genes only one parent has are rolled fresh. */
        fun cross(a: Genome, b: Genome, plan: GenePlan, lookup: GeneLookup, random: RandomSource): Genome {
            val child = (plan.keys + (a.genotypes.keys intersect b.genotypes.keys)).mapNotNull { id ->
                val def = lookup(id)
                val fromA = a[id]
                val fromB = b[id]
                val genotype = when {
                    def == null -> null
                    fromA != null && fromB != null ->
                        Genotype(def.mutate(fromA.pick(random), random), def.mutate(fromB.pick(random), random))
                    else -> roll(id, plan, lookup, random)
                }
                genotype?.let { id to it }
            }
            return Genome(child.toMap())
        }

        private fun roll(id: ResourceLocation, plan: GenePlan, lookup: GeneLookup, random: RandomSource): Genotype? {
            val def = lookup(id) ?: return null
            val override = plan[id]
            val pool = if (override == null || override.isEmpty) def.pool else override
            return pool.roll(random) ?: def.pool.roll(random)
        }
    }
}
