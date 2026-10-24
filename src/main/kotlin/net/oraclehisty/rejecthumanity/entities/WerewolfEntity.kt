package net.oraclehisty.rejecthumanity.entities

import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.oraclehisty.rejecthumanity.faction.Faction
import net.oraclehisty.rejecthumanity.race.RaceRegistry

/** Canine humanoids with their own model; coat, ears, tail and build come from `race/werewolf.json`. */
class WerewolfEntity(type: EntityType<out WerewolfEntity>, level: Level) : RHRaceEntity(type, level) {
    // Getters, not stored fields: Mob's constructor calls into subclass code before field initializers run.
    override val raceKey get() = RACE
    override val faction get() = Faction.WEREWOLVES

    companion object {
        val RACE = RaceRegistry.key("werewolf")
    }
}
