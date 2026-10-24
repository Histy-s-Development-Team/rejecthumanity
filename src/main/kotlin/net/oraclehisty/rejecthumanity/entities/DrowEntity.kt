package net.oraclehisty.rejecthumanity.entities

import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.oraclehisty.rejecthumanity.faction.Faction
import net.oraclehisty.rejecthumanity.race.RaceRegistry

/** Dark elves: everything about how they look and inherit comes from `race/drow.json`. */
class DrowEntity(type: EntityType<out DrowEntity>, level: Level) : RHRaceEntity(type, level) {
    // Getters, not stored fields: Mob's constructor calls into subclass code before field initializers run.
    override val raceKey get() = RACE
    override val faction get() = Faction.DROW

    companion object {
        val RACE = RaceRegistry.key("drow")
    }
}
