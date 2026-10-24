package net.oraclehisty.rejecthumanity.entities

import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import kotlin.math.sin

/**
 * Cosmetic spring physics for soft parts (driven client-side only). Each channel is an independent damped spring
 * with its own stiffness, so paired parts (left/right) fall out of sync instead of moving as one block.
 * Springs are pushed by footsteps and by vertical acceleration (jumping, landing, falling).
 * Output is roughly -1..1; appearance rules turn it into degrees.
 */
class SoftBody(private val stiffness: FloatArray = floatArrayOf(0.30f, 0.36f)) {
    private val position = FloatArray(stiffness.size)
    private val previous = FloatArray(stiffness.size)
    private val velocity = FloatArray(stiffness.size)
    private var lastVerticalSpeed = 0.0

    fun tick(entity: LivingEntity) {
        val verticalSpeed = entity.y - entity.yo
        val acceleration = (verticalSpeed - lastVerticalSpeed).toFloat()
        lastVerticalSpeed = verticalSpeed

        // One push per footstep (twice per walk cycle), strongest at full walking speed.
        val walk = entity.walkAnimation
        val step = sin(walk.position() * 0.6662f * 2f) * walk.speed().coerceAtMost(1f)

        for (i in position.indices) {
            previous[i] = position[i]
            val force = step * 0.25f - acceleration * 3f - stiffness[i] * position[i]
            velocity[i] = velocity[i] * DAMPING + force
            position[i] = (position[i] + velocity[i]).coerceIn(-1.5f, 1.5f)
        }
    }

    /** Interpolated spring offset for [channel] (wraps if a rule asks for more channels than exist). */
    fun value(channel: Int, partialTick: Float): Float {
        val i = Math.floorMod(channel, position.size)
        return Mth.lerp(partialTick, previous[i], position[i])
    }

    private companion object {
        const val DAMPING = 0.82f
    }
}
