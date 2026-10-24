package net.oraclehisty.rejecthumanity.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FastColor
import net.oraclehisty.rejecthumanity.entities.RHRaceEntity
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.renderer.GeoRenderer
import software.bernie.geckolib.renderer.layer.GeoRenderLayer

/** Draws every overlay layer of the entity's race appearance, in order, each with its own texture, tint and alpha. */
class AppearanceLayer<T : RHRaceEntity>(renderer: GeoRenderer<T>) : GeoRenderLayer<T>(renderer) {
    override fun render(
        poseStack: PoseStack,
        animatable: T,
        bakedModel: BakedGeoModel,
        renderType: RenderType?,
        bufferSource: MultiBufferSource,
        buffer: VertexConsumer?,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        if (animatable.isInvisible) return
        val appearance = animatable.appearance ?: return
        val phenotype = animatable.phenotype
        val seed = animatable.appearanceSeed

        val blinking = isBlinking(animatable, seed)

        for (layer in appearance.layers) {
            if (layer.hideOnBlink && blinking) continue
            if (layer.adultOnly && animatable.isBaby) continue
            if (layer.whenCondition != null && !layer.whenCondition.test(phenotype)) continue
            val texture = layer.resolveTexture(phenotype, seed)?.takeIf(::textureExists) ?: continue
            val alpha = layer.alpha?.evaluate(phenotype)?.coerceIn(0f, 1f) ?: 1f
            if (alpha <= 0.01f) continue

            val layerType = when {
                layer.emissive -> RenderType.eyes(texture)
                layer.alpha != null -> RenderType.entityTranslucent(texture)
                else -> RenderType.entityCutoutNoCull(texture)
            }
            val color = FastColor.ARGB32.color((alpha * 255).toInt(), appearance.tint(layer.tint, phenotype, seed))
            val light = if (layer.emissive) LightTexture.FULL_BRIGHT else packedLight

            renderer.reRender(
                bakedModel, poseStack, bufferSource, animatable, layerType,
                bufferSource.getBuffer(layerType), partialTick, light, packedOverlay, color,
            )
        }
    }

    companion object {
        /** Eyes close for 3 ticks every 4–6 seconds, offset per individual so a crowd doesn't blink in sync. Always closed when asleep. */
        private fun isBlinking(entity: RHRaceEntity, seed: Long): Boolean {
            if (entity.isSleeping) return true
            val period = 80 + Math.floorMod(seed, 41L)
            return Math.floorMod(entity.tickCount + seed, period) < 3
        }

        // A datapack can point at art that doesn't exist; skip it instead of drawing the missing-texture checkerboard.
        private val existing = HashMap<ResourceLocation, Boolean>()

        private fun textureExists(texture: ResourceLocation) = existing.getOrPut(texture) {
            Minecraft.getInstance().resourceManager.getResource(texture).isPresent
        }

        fun clearCache() = existing.clear()
    }
}
