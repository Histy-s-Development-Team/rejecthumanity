package net.oraclehisty.rejecthumanity.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.genetics.AllelePool
import net.oraclehisty.rejecthumanity.genetics.GenePlan
import net.oraclehisty.rejecthumanity.helpers.id
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

/** Partners must express different values of [differOn] (e.g. sex) to breed. */
data class Mating(val differOn: ResourceLocation?) {
    companion object {
        val NONE = Mating(null)
        val CODEC: Codec<Mating> = RecordCodecBuilder.create { i ->
            i.group(ResourceLocation.CODEC.nullableFieldOf("differ_on", Mating::differOn)).apply(i) { differOn -> Mating(differOn.orNull()) }
        }
    }
}

/** A datapack-defined race (`data/<namespace>/rejecthumanity/race/<name>.json`): which genes it carries and how they look. */
data class RaceDefinition(val genes: GenePlan, val mating: Mating, val appearance: Appearance) {
    companion object {
        val CODEC: Codec<RaceDefinition> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.unboundedMap(ResourceLocation.CODEC, AllelePool.CODEC).fieldOf("genes").forGetter(RaceDefinition::genes),
                Mating.CODEC.optionalFieldOf("mating", Mating.NONE).forGetter(RaceDefinition::mating),
                Appearance.CODEC.fieldOf("appearance").forGetter(RaceDefinition::appearance),
            ).apply(i, ::RaceDefinition)
        }
    }
}

object RaceRegistry {
    val KEY: ResourceKey<Registry<RaceDefinition>> = ResourceKey.createRegistryKey("race".id())

    fun key(name: String): ResourceKey<RaceDefinition> = ResourceKey.create(KEY, name.id())

    fun get(access: RegistryAccess, key: ResourceKey<RaceDefinition>): RaceDefinition? =
        access.registry(KEY).flatMap { it.getOptional(key) }.orElse(null)
}
