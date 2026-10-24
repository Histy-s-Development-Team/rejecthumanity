package net.oraclehisty.rejecthumanity.client

import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.entities.RHRaceEntity
import net.oraclehisty.rejecthumanity.helpers.id
import software.bernie.geckolib.animation.AnimationState
import software.bernie.geckolib.model.DefaultedEntityGeoModel

/** One model class for every race: model, animations, base texture and bone rules all come from the race's appearance. */
class RaceGeoModel<T : RHRaceEntity> : DefaultedEntityGeoModel<T>(FALLBACK, "Head") {
    override fun getModelResource(animatable: T): ResourceLocation =
        animatable.appearance?.model?.withPath { "geo/entity/$it.geo.json" } ?: super.getModelResource(animatable)

    override fun getAnimationResource(animatable: T): ResourceLocation =
        animatable.appearance?.model?.withPath { "animations/entity/$it.animation.json" } ?: super.getAnimationResource(animatable)

    override fun getTextureResource(animatable: T): ResourceLocation =
        animatable.appearance?.base?.resolveTexture(animatable.phenotype, animatable.appearanceSeed)
            ?: super.getTextureResource(animatable)

    override fun setCustomAnimations(animatable: T, instanceId: Long, animationState: AnimationState<T>) {
        super.setCustomAnimations(animatable, instanceId, animationState)
        val appearance = animatable.appearance ?: return
        val phenotype = animatable.phenotype
        // Bones are shared by every entity using this model, so each rule is re-applied every frame.
        for (rule in appearance.bones) {
            val bone = getBone(rule.bone).orElse(null) ?: continue
            rule.visibleIf?.let { bone.isHidden = !it.test(phenotype) }
            rule.scale?.let {
                val scale = it.evaluate(phenotype)
                bone.scaleX = scale
                bone.scaleY = scale
                bone.scaleZ = scale
            }
        }
    }

    companion object {
        private val FALLBACK = "genericelf".id()
    }
}
