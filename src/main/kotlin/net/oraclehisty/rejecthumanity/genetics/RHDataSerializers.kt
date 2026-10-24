package net.oraclehisty.rejecthumanity.genetics

import net.minecraft.network.syncher.EntityDataSerializer
import net.neoforged.neoforge.registries.NeoForgeRegistries
import net.oraclehisty.rejecthumanity.helpers.SingletonRegister

object RHDataSerializers : SingletonRegister<EntityDataSerializer<*>>(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS) {
    val GENOME: EntityDataSerializer<Genome> = EntityDataSerializer.forValueType(Genome.STREAM_CODEC)

    init {
        create("genome") { GENOME }
    }
}
