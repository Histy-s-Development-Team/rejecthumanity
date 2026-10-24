package net.oraclehisty.rejecthumanity.entities

import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.oraclehisty.rejecthumanity.faction.Faction
import net.oraclehisty.rejecthumanity.race.RaceRegistry

class ElfEntity(type: EntityType<out ElfEntity>, level: Level) : RHRaceEntity(type, level) {
    // Getters, not stored fields: Mob's constructor calls into subclass code before field initializers run.
    override val raceKey get() = RACE
    override val faction get() = Faction.ELVES

    companion object {
        val RACE = RaceRegistry.key("elf")
    }
}
