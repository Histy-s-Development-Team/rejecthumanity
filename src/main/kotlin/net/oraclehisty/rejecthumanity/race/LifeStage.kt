package net.oraclehisty.rejecthumanity.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/** Growth stages every race goes through. Vanilla still sees babies and teens as "baby" (no breeding). */
enum class LifeStage {
    BABY,
    TEEN,
    ADULT;

    val young get() = this != ADULT

    companion object {
        fun byId(id: Int) = entries.getOrElse(id) { ADULT }
    }
}

/**
 * How a race grows up: ticks spent as a baby and then as a teen (24000 ticks = one Minecraft day), and the size
 * of each stage relative to an adult.
 */
data class Growth(val babyTicks: Int, val teenTicks: Int, val babyScale: Float, val teenScale: Float) {
    /** Age (vanilla counts up from negative to 0) at which a newborn starts. */
    val newbornAge get() = -(babyTicks + teenTicks)

    fun stageAt(age: Int) = when {
        age >= 0 -> LifeStage.ADULT
        age >= -teenTicks -> LifeStage.TEEN
        else -> LifeStage.BABY
    }

    fun scaleOf(stage: LifeStage) = when (stage) {
        LifeStage.BABY -> babyScale
        LifeStage.TEEN -> teenScale
        LifeStage.ADULT -> 1f
    }

    companion object {
        val DEFAULT = Growth(24000, 24000, 0.5f, 0.9f)

        val CODEC: Codec<Growth> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.optionalFieldOf("baby_ticks", DEFAULT.babyTicks).forGetter(Growth::babyTicks),
                Codec.INT.optionalFieldOf("teen_ticks", DEFAULT.teenTicks).forGetter(Growth::teenTicks),
                Codec.FLOAT.optionalFieldOf("baby_scale", DEFAULT.babyScale).forGetter(Growth::babyScale),
                Codec.FLOAT.optionalFieldOf("teen_scale", DEFAULT.teenScale).forGetter(Growth::teenScale),
            ).apply(i, ::Growth)
        }
    }
}
