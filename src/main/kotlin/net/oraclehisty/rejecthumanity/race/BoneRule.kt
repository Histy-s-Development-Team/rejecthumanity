package net.oraclehisty.rejecthumanity.race

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.StringRepresentable
import net.oraclehisty.rejecthumanity.genetics.Condition
import net.oraclehisty.rejecthumanity.helpers.nullableFieldOf
import net.oraclehisty.rejecthumanity.helpers.orNull

/** A moment in an entity's life that a bone rule can be limited to. */
enum class BodyState(private val serialized: String) : StringRepresentable {
    AGGRESSIVE("aggressive"),
    MOVING("moving"),
    IDLE("idle"),
    SLEEPING("sleeping"),
    BABY("baby"),
    TEEN("teen"),
    ADULT("adult");

    override fun getSerializedName() = serialized

    companion object {
        val CODEC: Codec<BodyState> = StringRepresentable.fromEnum(::values)
    }
}

/** Which of x/y/z something applies to, written like "xyz" or "z". */
@JvmInline
value class Axes(val letters: String) {
    val x get() = 'x' in letters
    val y get() = 'y' in letters
    val z get() = 'z' in letters

    companion object {
        val ALL = Axes("xyz")
        val X = Axes("x")

        val CODEC: Codec<Axes> = Codec.STRING.comapFlatMap(
            { text ->
                if (text.isNotEmpty() && text.all { it in "xyz" }) DataResult.success(Axes(text))
                else DataResult.error { "Axes must be letters from \"xyz\", got \"$text\"" }
            },
            Axes::letters,
        )
    }
}

/**
 * Shows/hides, scales and rotates model bones. `"bone"` is one name or a list.
 * - [scale] applies to the [axes] given (so a gene can lengthen ears outward without thickening them)
 * - [rotate] is an angle in degrees added to the bone's pose on [rotateAxes], e.g. from a size gene
 * - [jiggle] is a bounce amplitude in degrees on [rotateAxes] (a number, or a gene rule so it grows with e.g. size),
 *   driven by the entity's spring physics; [jiggleChannel] picks which spring, so paired parts move independently
 * - [state] limits the rule to a moment in the entity's life (put it after the rules it should override, e.g. ears
 *   pinned back while fighting)
 */
data class BoneRule(
    val bones: List<String>,
    val visibleIf: Condition?,
    val scale: ScaleRule?,
    val axes: Axes,
    val rotate: ScaleRule?,
    val rotateAxes: Axes,
    val jiggle: ScaleRule?,
    val jiggleChannel: Int,
    val state: BodyState?,
) {
    val rotates get() = rotate != null || jiggle != null

    companion object {
        private val NAMES: Codec<List<String>> = Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(
            { either -> either.map({ listOf(it) }, { it }) },
            { names -> if (names.size == 1) Either.left<String, List<String>>(names[0]) else Either.right<String, List<String>>(names) },
        )

        /** A plain number, or a full gene-driven rule. */
        private val AMOUNT: Codec<ScaleRule> = Codec.either(Codec.FLOAT, ScaleRule.CODEC).xmap(
            { either -> either.map(ScaleRule::constant) { it } },
            { Either.right<Float, ScaleRule>(it) },
        )

        val CODEC: Codec<BoneRule> = RecordCodecBuilder.create { i ->
            i.group(
                NAMES.fieldOf("bone").forGetter(BoneRule::bones),
                Condition.CODEC.nullableFieldOf("visible_if", BoneRule::visibleIf),
                ScaleRule.CODEC.nullableFieldOf("scale", BoneRule::scale),
                Axes.CODEC.optionalFieldOf("axes", Axes.ALL).forGetter(BoneRule::axes),
                ScaleRule.CODEC.nullableFieldOf("rotate", BoneRule::rotate),
                Axes.CODEC.optionalFieldOf("rotate_axes", Axes.X).forGetter(BoneRule::rotateAxes),
                AMOUNT.nullableFieldOf("jiggle", BoneRule::jiggle),
                Codec.INT.optionalFieldOf("jiggle_channel", 0).forGetter(BoneRule::jiggleChannel),
                BodyState.CODEC.nullableFieldOf("state", BoneRule::state),
            ).apply(i) { bones, visibleIf, scale, axes, rotate, rotateAxes, jiggle, jiggleChannel, state ->
                BoneRule(bones, visibleIf.orNull(), scale.orNull(), axes, rotate.orNull(), rotateAxes, jiggle.orNull(), jiggleChannel, state.orNull())
            }
        }
    }
}
