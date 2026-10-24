package net.oraclehisty.rejecthumanity.client

import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.oraclehisty.rejecthumanity.entities.RHRaceEntity
import software.bernie.geckolib.renderer.GeoEntityRenderer
import software.bernie.geckolib.util.Color

/** Renderer shared by every race. The base pass is tinted by the race's base layer (skin); overlays come from [AppearanceLayer]. */
class RaceRenderer<T : RHRaceEntity>(context: EntityRendererProvider.Context) : GeoEntityRenderer<T>(context, RaceGeoModel()) {
    init {
        shadowRadius = 0.5f
        addRenderLayer(AppearanceLayer(this))
    }

    override fun getRenderColor(animatable: T, partialTick: Float, packedLight: Int): Color {
        val color = super.getRenderColor(animatable, partialTick, packedLight)
        val appearance = animatable.appearance ?: return color
        val rgb = appearance.tint(appearance.base.tint, animatable.phenotype, animatable.appearanceSeed)
        return Color.ofARGB(color.alpha, (rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
    }
}
