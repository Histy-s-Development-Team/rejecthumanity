package net.oraclehisty.rejecthumanity.client.animation

import net.minecraft.world.entity.LivingEntity
import software.bernie.geckolib.cache.`object`.GeoBone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural locomotion for any model using the vanilla humanoid bone names (Head, Body, RightArm, LeftArm,
 * RightLeg, LeftLeg): a port of vanilla HumanoidModel.setupAnim (walk, idle arm sway, riding pose, melee swing).
 * On four-legged models with the same names (front legs = arms) the opposite-phase swing becomes a trot.
 *
 * Models with RightShin/RightPaw/LeftShin/LeftPaw under the legs also get a digitigrade stride.
 */
object HumanoidAnimator {
    private const val PI_F = PI.toFloat()

    /** Vanilla's arm/leg Z rotations point the other way in GeckoLib's frame. */
    private const val Z_SIGN = -1f

    private val UPPER_BODY = listOf("Body", "Head", "RightArm", "LeftArm")

    fun animate(bones: BoneLookup, entity: LivingEntity, gait: Gait) {
        val body = bones["Body"] ?: return
        val rightArm = bones["RightArm"] ?: return
        val leftArm = bones["LeftArm"] ?: return
        val rightLeg = bones["RightLeg"] ?: return
        val leftLeg = bones["LeftLeg"] ?: return
        val head = bones["Head"]

        val swing = gait.swing
        val amount = gait.amount

        var bodyY = 0f
        var rArmX = cos(swing + PI_F) * amount
        var lArmX = cos(swing) * amount
        var rArmY = 0f
        var lArmY = 0f
        var rArmZ = 0f
        var lArmZ = 0f
        var rLegX = cos(swing) * 1.4f * amount
        var lLegX = cos(swing + PI_F) * 1.4f * amount
        var rLegY = 0f
        var lLegY = 0f
        var rLegZ = 0f
        var lLegZ = 0f

        if (entity.isPassenger) {
            rArmX -= PI_F / 5f
            lArmX -= PI_F / 5f
            rLegX = -1.4137167f
            rLegY = PI_F / 10f
            rLegZ = 0.07853982f
            lLegX = -1.4137167f
            lLegY = -PI_F / 10f
            lLegZ = -0.07853982f
        }

        // Melee swing with the right (main) hand.
        val attack = entity.getAttackAnim(gait.partialTick)
        if (attack > 0f) {
            bodyY = sin(sqrt(attack) * (PI_F * 2f)) * 0.2f
            rArmY += bodyY
            lArmY += bodyY
            lArmX += bodyY
            val eased = 1f - (1f - attack).let { it * it * it * it }
            val lift = sin(eased * PI_F)
            val headPitch = head?.rotX ?: 0f
            val reach = sin(attack * PI_F) * -(headPitch - 0.7f) * 0.75f
            rArmX -= lift * 1.2f + reach
            rArmY += bodyY * 2f
            rArmZ += sin(attack * PI_F) * -0.4f
        }

        // Gentle idle sway (vanilla AnimationUtils.bobArms).
        val sway = cos(gait.age * 0.09f) * 0.05f + 0.05f
        val bob = sin(gait.age * 0.067f) * 0.05f
        rArmZ += sway
        lArmZ -= sway
        rArmX += bob
        lArmX -= bob

        body.pose(0f, bodyY, 0f)
        rightArm.pose(rArmX, rArmY, rArmZ * Z_SIGN)
        leftArm.pose(lArmX, lArmY, lArmZ * Z_SIGN)
        rightLeg.pose(rLegX, rLegY, rLegZ * Z_SIGN)
        leftLeg.pose(lLegX, lLegY, lLegZ * Z_SIGN)
        animateDigitigrade(bones, gait, rLegX, lLegX)
    }

    /**
     * The hock folds while each leg swings forward to lift the paw, then straightens through the stance; the paw
     * stays level with the ground, tips up to reach as it lifts and flicks down at push-off. The upper body sinks
     * onto each planted leg (twice per cycle) while the legs stay put, so the dip reads against the ground.
     */
    private fun animateDigitigrade(bones: BoneLookup, gait: Gait, rightHip: Float, leftHip: Float) {
        val rightShin = bones["RightShin"] ?: return
        val rightPaw = bones["RightPaw"] ?: return
        val leftShin = bones["LeftShin"] ?: return
        val leftPaw = bones["LeftPaw"] ?: return
        stride(rightShin, rightPaw, gait.swing, gait.amount, rightHip)
        stride(leftShin, leftPaw, gait.swing + PI_F, gait.amount, leftHip)
        val dip = -abs(sin(gait.swing)) * gait.amount * 2.4f
        for (name in UPPER_BODY) bones[name]?.posY = dip
    }

    /** [phase] matches the hip's walk phase: the leg swings forward while sin(phase) < 0. */
    private fun stride(shin: GeoBone, paw: GeoBone, phase: Float, amount: Float, hip: Float) {
        val lift = max(0f, -sin(phase)) * amount    // recovery: leg travelling forward, off the ground
        val pushOff = max(0f, -cos(phase)) * amount // end of stance: leg furthest back
        // Runtime +X swings a part's lower end forward, so folding the hock further is negative.
        val shinAngle = -lift * 1.2f
        shin.pose(shinAngle, 0f, 0f)
        // Cancel the hip and hock so the paw stays level, then add the reach and the push-off flick.
        paw.pose(-(hip + shinAngle) + lift * 0.35f - pushOff * 0.45f, 0f, 0f)
    }

    /** Rotation relative to the bone's bind pose, in radians. */
    private fun GeoBone.pose(x: Float, y: Float, z: Float) {
        val bind = initialSnapshot
        rotX = bind.rotX + x
        rotY = bind.rotY + y
        rotZ = bind.rotZ + z
    }
}
