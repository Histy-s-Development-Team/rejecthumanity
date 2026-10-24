package net.oraclehisty.rejecthumanity.client.animation

import net.oraclehisty.rejecthumanity.entities.RHRaceEntity
import net.oraclehisty.rejecthumanity.race.Appearance
import net.oraclehisty.rejecthumanity.race.BodyState
import net.oraclehisty.rejecthumanity.race.LifeStage
import org.joml.Vector3f

/**
 * Applies a race's bone rules (visibility, gene-driven scale/rotation, spring jiggle) for one individual.
 *
 * Bones are shared by every entity drawn with the same model, so anything a rule touched for the previous
 * individual (a teen-only scale, ears pinned back while fighting) is first restored. Rotation rules add to the
 * bind pose, or to the pose a running keyframe animation (e.g. an ear twitch) gave the bone this frame, so rules
 * and keyframes combine instead of one erasing the other.
 */
object BoneRuleApplier {
    fun apply(bones: BoneLookup, entity: RHRaceEntity, appearance: Appearance, gait: Gait, keyframed: Set<String>) {
        val phenotype = entity.phenotype
        val basePose = HashMap<String, Vector3f>()

        for (name in appearance.ruleBones) {
            val bone = bones[name] ?: continue
            val bind = bone.initialSnapshot
            bone.isHidden = false
            bone.scaleX = bind.scaleX
            bone.scaleY = bind.scaleY
            bone.scaleZ = bind.scaleZ
            if (name in appearance.rotatedBones) {
                val pose = if (name in keyframed) Vector3f(bone.rotX, bone.rotY, bone.rotZ) else Vector3f(bind.rotX, bind.rotY, bind.rotZ)
                basePose[name] = pose
                bone.rotX = pose.x
                bone.rotY = pose.y
                bone.rotZ = pose.z
            }
        }

        for (rule in appearance.bonesFor(entity.lifeStage)) {
            if (rule.state != null && !inState(entity, rule.state, gait)) continue
            val visible = rule.visibleIf?.test(phenotype)
            val scale = rule.scale?.evaluate(phenotype)
            val angle = if (rule.rotates) {
                val jiggle = rule.jiggle?.let { it.evaluate(phenotype, 0f) * entity.softBody.value(rule.jiggleChannel, gait.partialTick) } ?: 0f
                Math.toRadians(((rule.rotate?.evaluate(phenotype, 0f) ?: 0f) + jiggle).toDouble()).toFloat()
            } else 0f

            for (name in rule.bones) {
                val bone = bones[name] ?: continue
                if (visible != null) bone.isHidden = !visible
                if (scale != null) {
                    bone.scaleX = if (rule.axes.x) scale else 1f
                    bone.scaleY = if (rule.axes.y) scale else 1f
                    bone.scaleZ = if (rule.axes.z) scale else 1f
                }
                if (rule.rotates) {
                    val pose = basePose[name] ?: continue
                    if (rule.rotateAxes.x) bone.rotX = pose.x + angle
                    if (rule.rotateAxes.y) bone.rotY = pose.y + angle
                    if (rule.rotateAxes.z) bone.rotZ = pose.z + angle
                }
            }
        }
    }

    private fun inState(entity: RHRaceEntity, state: BodyState, gait: Gait) = when (state) {
        BodyState.AGGRESSIVE -> entity.isAggressive
        BodyState.MOVING -> gait.moving
        BodyState.IDLE -> !gait.moving
        BodyState.SLEEPING -> entity.isSleeping
        BodyState.BABY -> entity.lifeStage == LifeStage.BABY
        BodyState.TEEN -> entity.lifeStage == LifeStage.TEEN
        BodyState.ADULT -> entity.lifeStage == LifeStage.ADULT
    }
}
