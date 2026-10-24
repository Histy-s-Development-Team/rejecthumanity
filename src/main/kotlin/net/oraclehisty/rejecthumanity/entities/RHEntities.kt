package net.oraclehisty.rejecthumanity.entities

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.SpawnPlacementTypes
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent
import net.oraclehisty.rejecthumanity.helpers.SingletonRegister

object RHEntities: SingletonRegister<EntityType<*>>(BuiltInRegistries.ENTITY_TYPE) {
    val ELF by create("elf", ::ElfEntity, MobCategory.CREATURE) {
        sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
    }

    fun <E : Entity> create(
        id: String,
        factory: EntityType.EntityFactory<E>,
        category: MobCategory,
        block: EntityType.Builder<E>.() -> Unit,
    ): Lazy<EntityType<E>> = create(id) {
        EntityType.Builder.of(factory, category).also(block).build(id)
    }

    fun registerAttributes(event: EntityAttributeCreationEvent) {
        event.put(ELF, RHRaceEntity.createAttributes().build())
    }

    fun registerSpawnPlacements(event: RegisterSpawnPlacementsEvent) {
        event.register(
            ELF, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            { type, level, spawnType, pos, random -> Animal.checkAnimalSpawnRules(type, level, spawnType, pos, random) },
            RegisterSpawnPlacementsEvent.Operation.REPLACE,
        )
    }
}
