package net.oraclehisty.rejecthumanity.client

import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.entities.RHRaceEntity
import net.oraclehisty.rejecthumanity.helpers.id
import software.bernie.geckolib.animation.AnimationState
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.model.DefaultedEntityGeoModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One model class for every race: model, animations, base texture and bone rules all come from the race's appearance.
 * Models that use the vanilla humanoid bone names (Head, Body, RightArm, LeftArm, RightLeg, LeftLeg) get vanilla
 * HumanoidModel locomotion for free; keyframed animations (e.g. the ear twitch) play on top.
 */
class RaceGeoModel<T : RHRaceEntity> : DefaultedEntityGeoModel<T>(FALLBACK, "Head") {
    override fun getModelResource(animatable: T): ResourceLocation =
        animatable.appearance?.modelFor(animatable.isBaby)?.withPath { "geo/entity/$it.geo.json" } ?: super.getModelResource(animatable)

    override fun getAnimationResource(animatable: T): ResourceLocation =
        animatable.appearance?.modelFor(animatable.isBaby)?.withPath { "animations/entity/$it.animation.json" } ?: super.getAnimationResource(animatable)

    override fun getTextureResource(animatable: T): ResourceLocation =
        animatable.appearance?.base?.resolveTexture(animatable.phenotype, animatable.appearanceSeed)
            ?: super.getTextureResource(animatable)

    override fun setCustomAnimations(animatable: T, instanceId: Long, animationState: AnimationState<T>) {
        super.setCustomAnimations(animatable, instanceId, animationState)
        animateHumanoid(animatable, animationState)
        applyBoneRules(animatable)
    }

    // Bones are shared by every entity using this model, so everything here is re-applied every frame.
    private fun applyBoneRules(animatable: T) {
        val appearance = animatable.appearance ?: return
        val phenotype = animatable.phenotype
        for (rule in appearance.bonesFor(animatable.isBaby)) {
            val visible = rule.visibleIf?.test(phenotype)
            val scale = rule.scale?.evaluate(phenotype)
            for (name in rule.bones) {
                val bone = getBone(name).orElse(null) ?: continue
                if (visible != null) bone.isHidden = !visible
                if (scale != null) {
                    bone.scaleX = if ('x' in rule.axes) scale else 1f
                    bone.scaleY = if ('y' in rule.axes) scale else 1f
                    bone.scaleZ = if ('z' in rule.axes) scale else 1f
                }
            }
        }
    }

    /** A port of vanilla HumanoidModel.setupAnim: walk cycle, idle arm sway, riding pose and melee swing. */
    private fun animateHumanoid(entity: T, state: AnimationState<T>) {
        val body = bone("Body") ?: return
        val rightArm = bone("RightArm") ?: return
        val leftArm = bone("LeftArm") ?: return
        val rightLeg = bone("RightLeg") ?: return
        val leftLeg = bone("LeftLeg") ?: return
        val head = bone("Head")

        val partialTick = state.partialTick
        val swing = state.limbSwing * 0.6662f
        val amount = state.limbSwingAmount.coerceAtMost(1f)
        val age = entity.tickCount + partialTick

        var bodyY = 0f
        var rArmX = cos(swing + PI.toFloat()) * amount
        var lArmX = cos(swing) * amount
        var rArmY = 0f
        var lArmY = 0f
        var rArmZ = 0f
        var lArmZ = 0f
        var rLegX = cos(swing) * 1.4f * amount
        var lLegX = cos(swing + PI.toFloat()) * 1.4f * amount
        var rLegY = 0f
        var lLegY = 0f
        var rLegZ = 0f
        var lLegZ = 0f

        if (entity.isPassenger) {
            rArmX -= PI.toFloat() / 5f
            lArmX -= PI.toFloat() / 5f
            rLegX = -1.4137167f
            rLegY = PI.toFloat() / 10f
            rLegZ = 0.07853982f
            lLegX = -1.4137167f
            lLegY = -PI.toFloat() / 10f
            lLegZ = -0.07853982f
        }

        // Melee swing with the right (main) hand.
        val attack = entity.getAttackAnim(partialTick)
        if (attack > 0f) {
            bodyY = sin(sqrt(attack) * (PI.toFloat() * 2f)) * 0.2f
            rArmY += bodyY
            lArmY += bodyY
            lArmX += bodyY
            val eased = 1f - (1f - attack).let { it * it * it * it }
            val lift = sin(eased * PI.toFloat())
            val headPitch = head?.rotX ?: 0f
            val reach = sin(attack * PI.toFloat()) * -(headPitch - 0.7f) * 0.75f
            rArmX -= lift * 1.2f + reach
            rArmY += bodyY * 2f
            rArmZ += sin(attack * PI.toFloat()) * -0.4f
        }

        // Gentle idle sway (vanilla AnimationUtils.bobArms).
        val sway = cos(age * 0.09f) * 0.05f + 0.05f
        val bob = sin(age * 0.067f) * 0.05f
        rArmZ += sway
        lArmZ -= sway
        rArmX += bob
        lArmX -= bob

        body.rotY = bodyY
        rightArm.set(rArmX, rArmY, rArmZ * Z_SIGN)
        leftArm.set(lArmX, lArmY, lArmZ * Z_SIGN)
        rightLeg.set(rLegX, rLegY, rLegZ * Z_SIGN)
        leftLeg.set(lLegX, lLegY, lLegZ * Z_SIGN)
    }

    private fun bone(name: String): GeoBone? = getBone(name).orElse(null)

    private fun GeoBone.set(x: Float, y: Float, z: Float) {
        val bind = initialSnapshot
        rotX = bind.rotX + x
        rotY = bind.rotY + y
        rotZ = bind.rotZ + z
    }

    companion object {
        private val FALLBACK = "elf".id()

        /** Vanilla and GeckoLib agree on X/Y rotation direction; Z is mirrored between the two. */
        private const val Z_SIGN = -1f
    }
}
