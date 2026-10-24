package net.oraclehisty.rejecthumanity.client.animation

import net.minecraft.world.entity.LivingEntity
import software.bernie.geckolib.cache.`object`.GeoBone

/** Looks up a bone of the model being animated this frame. */
fun interface BoneLookup {
    operator fun get(name: String): GeoBone?
}

/**
 * Walk inputs for one frame.
 * - [swing]: walk-cycle phase in radians (vanilla's `limbSwing * 0.6662`)
 * - [amount]: how strongly the limbs swing, 0..1
 * - [age]: ticks lived, for idle motion
 */
data class Gait(val swing: Float, val amount: Float, val partialTick: Float, val age: Float) {
    val moving get() = amount > MOVING_THRESHOLD

    companion object {
        private const val MOVING_THRESHOLD = 0.15f

        /**
         * GeckoLib speeds the walk cycle up 3x for anything vanilla calls a baby, which includes teens. Instead, the
         * cadence follows body size: shorter legs take proportionally quicker steps (teens ~1.1x, babies 2x).
         */
        fun of(entity: LivingEntity, partialTick: Float): Gait {
            val walking = !entity.isPassenger && entity.isAlive
            val cadence = 1f / entity.ageScale
            val swing = if (walking) entity.walkAnimation.position(partialTick) * cadence * 0.6662f else 0f
            val amount = if (walking) entity.walkAnimation.speed(partialTick).coerceAtMost(1f) else 0f
            return Gait(swing, amount, partialTick, entity.tickCount + partialTick)
        }
    }
}
