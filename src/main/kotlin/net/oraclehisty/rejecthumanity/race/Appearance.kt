package net.oraclehisty.rejecthumanity.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.genetics.Phenotype
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

/**
 * What changes at a younger life stage (the race's "baby" and "teen" sections): an optional separate [model] (same
 * bone names, so animations and adult bone rules still apply where the bones exist) and extra [bones] rules applied
 * after the adult ones, so they can override them.
 */
data class StageAppearance(val model: ResourceLocation?, val bones: List<BoneRule>) {
    companion object {
        val NONE = StageAppearance(null, emptyList())
        val CODEC: Codec<StageAppearance> = RecordCodecBuilder.create { i ->
            i.group(
                ResourceLocation.CODEC.nullableFieldOf("model", StageAppearance::model),
                BoneRule.CODEC.listOf().optionalFieldOf("bones", emptyList()).forGetter(StageAppearance::bones),
            ).apply(i) { model, bones -> StageAppearance(model.orNull(), bones) }
        }
    }
}

/**
 * How a race turns a phenotype into visuals. [model] names `geo/entity/<path>.geo.json` and
 * `animations/entity/<path>.animation.json`. [scale] rules multiply together into the entity's size.
 * Pieces: [Palette], [Tint], [Layer], [BoneRule], [ScaleRule].
 */
data class Appearance(
    val model: ResourceLocation,
    val base: Layer,
    val layers: List<Layer>,
    val bones: List<BoneRule>,
    val scale: List<ScaleRule>,
    val palettes: Map<String, Palette>,
    val baby: StageAppearance,
    val teen: StageAppearance,
) {
    private fun stage(stage: LifeStage) = when (stage) {
        LifeStage.BABY -> baby
        LifeStage.TEEN -> teen
        LifeStage.ADULT -> StageAppearance.NONE
    }

    fun modelFor(stage: LifeStage) = stage(stage).model ?: model

    fun bonesFor(stage: LifeStage) = bones + stage(stage).bones

    private val allRules get() = bones + baby.bones + teen.bones

    /** Every bone any rule may touch at any stage; the renderer restores these each frame before applying rules. */
    val ruleBones: Set<String> by lazy { allRules.flatMap { it.bones }.toSet() }

    /** The subset whose rotation rules set. */
    val rotatedBones: Set<String> by lazy { allRules.filter { it.rotates }.flatMap { it.bones }.toSet() }

    fun scaleFor(phenotype: Phenotype) = scale.fold(1f) { acc, rule -> acc * rule.evaluate(phenotype) }

    fun tint(tint: Tint?, phenotype: Phenotype, seed: Long) = tint?.resolve(phenotype, palettes, seed) ?: 0xFFFFFF

    companion object {
        val CODEC: Codec<Appearance> = RecordCodecBuilder.create { i ->
            i.group(
                ResourceLocation.CODEC.fieldOf("model").forGetter(Appearance::model),
                Layer.CODEC.fieldOf("base").forGetter(Appearance::base),
                Layer.CODEC.listOf().optionalFieldOf("layers", emptyList()).forGetter(Appearance::layers),
                BoneRule.CODEC.listOf().optionalFieldOf("bones", emptyList()).forGetter(Appearance::bones),
                ScaleRule.CODEC.listOf().optionalFieldOf("scale", emptyList()).forGetter(Appearance::scale),
                Codec.unboundedMap(Codec.STRING, Palette.CODEC).optionalFieldOf("palettes", emptyMap()).forGetter(Appearance::palettes),
                StageAppearance.CODEC.optionalFieldOf("baby", StageAppearance.NONE).forGetter(Appearance::baby),
                StageAppearance.CODEC.optionalFieldOf("teen", StageAppearance.NONE).forGetter(Appearance::teen),
            ).apply(i, ::Appearance)
        }
    }
}
