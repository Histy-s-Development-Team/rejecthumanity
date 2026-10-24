package net.oraclehisty.rejecthumanity.client

import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.client.animation.BoneLookup
import net.oraclehisty.rejecthumanity.client.animation.BoneRuleApplier
import net.oraclehisty.rejecthumanity.client.animation.Gait
import net.oraclehisty.rejecthumanity.client.animation.HumanoidAnimator
import net.oraclehisty.rejecthumanity.entities.RHRaceEntity
import net.oraclehisty.rejecthumanity.helpers.id
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.AnimationState
import software.bernie.geckolib.model.DefaultedEntityGeoModel

/**
 * One model class for every race: the model, animations and base texture come from the race's appearance for the
 * individual's life stage. Each frame, after GeckoLib's keyframes and head tracking: [HumanoidAnimator] poses the
 * limbs, then [BoneRuleApplier] applies the race's bone rules.
 */
class RaceGeoModel<T : RHRaceEntity> : DefaultedEntityGeoModel<T>(FALLBACK, "Head") {
    private val bones = BoneLookup { getBone(it).orElse(null) }

    override fun getModelResource(animatable: T): ResourceLocation =
        animatable.appearance?.modelFor(animatable.lifeStage)?.withPath { "geo/entity/$it.geo.json" }
            ?: super.getModelResource(animatable)

    override fun getAnimationResource(animatable: T): ResourceLocation =
        animatable.appearance?.modelFor(animatable.lifeStage)?.withPath { "animations/entity/$it.animation.json" }
            ?: super.getAnimationResource(animatable)

    override fun getTextureResource(animatable: T): ResourceLocation =
        animatable.appearance?.base?.resolveTexture(animatable.phenotype, animatable.appearanceSeed)
            ?: super.getTextureResource(animatable)

    override fun setCustomAnimations(animatable: T, instanceId: Long, animationState: AnimationState<T>) {
        super.setCustomAnimations(animatable, instanceId, animationState)
        val gait = Gait.of(animatable, animationState.partialTick)
        HumanoidAnimator.animate(bones, animatable, gait)
        val appearance = animatable.appearance ?: return
        BoneRuleApplier.apply(bones, animatable, appearance, gait, keyframedBones(animatable, instanceId))
    }

    /** Bones a running keyframe animation (e.g. a triggered ear twitch) is driving this frame. */
    private fun keyframedBones(animatable: T, instanceId: Long): Set<String> =
        animatable.animatableInstanceCache.getManagerForId<T>(instanceId).animationControllers.values.asSequence()
            .filter { it.animationState == AnimationController.State.RUNNING }
            .mapNotNull { it.currentAnimation?.animation() }
            .flatMap { it.boneAnimations().asSequence() }
            .map { it.boneName() }
            .toSet()

    companion object {
        private val FALLBACK = "elf".id()
    }
}
