package net.oraclehisty.rejecthumanity.genetics

import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.oraclehisty.rejecthumanity.helpers.id

/** Datapack registry of genes: `data/<namespace>/rejecthumanity/gene/<name>.json`, synced to clients. */
object GeneRegistry {
    val KEY: ResourceKey<Registry<GeneDefinition>> = ResourceKey.createRegistryKey("gene".id())

    fun lookup(access: RegistryAccess): GeneLookup {
        val registry = access.registry(KEY).orElse(null) ?: return { null }
        return registry::get
    }
}
