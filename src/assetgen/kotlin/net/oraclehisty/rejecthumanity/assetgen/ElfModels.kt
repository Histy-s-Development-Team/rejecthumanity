package net.oraclehisty.rejecthumanity.assetgen

import net.oraclehisty.rejecthumanity.assetgen.PlayerSheet.BODY
import net.oraclehisty.rejecthumanity.assetgen.PlayerSheet.HAT
import net.oraclehisty.rejecthumanity.assetgen.PlayerSheet.HEAD
import net.oraclehisty.rejecthumanity.assetgen.PlayerSheet.JACKET

/** Which side of the body a paired part is on. Bedrock models face -z, so the model's right is -x. */
enum class Side(val sign: Double) {
    Right(-1.0), Left(1.0);
}

/**
 * Proportions of the elf-style humanoid (vanilla player rig + elf ears, chest and ponytails). The head is always the
 * 8×8×8 player head so face art lines up; everything else may be any size and samples the player layout stretched.
 *
 * Arms hang from the shoulder pivot ([armPivotDrop] below the neck); their top sits [armTopDrop] below the neck and
 * [shoulderGap] out from the torso. Legs sit [legInset] in from the centre line (vanilla's 0.1 avoids z-fighting).
 */
data class HumanoidProportions(
    val legLength: Double,
    val legWidth: Double,
    val legDepth: Double,
    val bodyWidth: Double,
    val bodyHeight: Double,
    val bodyDepth: Double,
    val armLength: Double,
    val armWidth: Double,
    val armDepth: Double,
    val legInset: Double = 0.1,
    val shoulderGap: Double = 0.0,
    val armTopDrop: Double = 0.0,
    val armPivotDrop: Double = 2.0,
    /** Width of the slim (XX) arm variant; null for a single arm set. */
    val slimArmWidth: Double? = 3.0,
    /** Size of the chest bones relative to an adult's; null for none. */
    val chestScale: Double? = 1.0,
    val ponytails: Boolean = true,
) {
    val hip get() = legLength
    val neck get() = legLength + bodyHeight
    val headTop get() = neck + 8

    companion object {
        /** The vanilla player's proportions. */
        val ADULT = HumanoidProportions(
            legLength = 12.0, legWidth = 4.0, legDepth = 4.0,
            bodyWidth = 8.0, bodyHeight = 12.0, bodyDepth = 4.0,
            armLength = 12.0, armWidth = 4.0, armDepth = 4.0,
        )

        /** Slighter than an adult: narrower, a little shorter in the body and legs, full-size head. */
        val TEEN = HumanoidProportions(
            legLength = 11.0, legWidth = 3.5, legDepth = 3.5,
            bodyWidth = 7.0, bodyHeight = 11.0, bodyDepth = 3.5,
            armLength = 11.0, armWidth = 3.5, armDepth = 3.5,
            chestScale = 0.75,
        )

        /** Full-size head on a short body and stubby limbs (the game also draws babies at half size). */
        val BABY = HumanoidProportions(
            legLength = 5.0, legWidth = 3.0, legDepth = 3.0, legInset = 0.0,
            bodyWidth = 6.0, bodyHeight = 7.0, bodyDepth = 3.0,
            armLength = 6.0, armWidth = 2.0, armDepth = 2.0,
            shoulderGap = 1.0, armTopDrop = 1.0, armPivotDrop = 1.0,
            slimArmWidth = null, chestScale = null, ponytails = false,
        )
    }
}

object ElfModels {
    fun all() = mapOf(
        "elf" to humanoid("geometry.rejecthumanity.elf", HumanoidProportions.ADULT),
        "elf_teen" to humanoid("geometry.rejecthumanity.elf_teen", HumanoidProportions.TEEN),
        "elf_baby" to humanoid("geometry.rejecthumanity.elf_baby", HumanoidProportions.BABY),
    )

    /** Ear twitch, played on a random timer by the entity; signs make both ears flick outward. */
    val animations = AnimationFile(mapOf("ear_twitch" to (0.45 to run {
        val twitch = Keyframes(listOf(
            0.0 to v(0, 0, 0), 0.08 to v(-6, 0, -14), 0.18 to v(2, 0, 5), 0.3 to v(-2, 0, -6), 0.45 to v(0, 0, 0),
        ))
        mapOf("RightEar" to twitch, "LeftEar" to twitch.mirroredZ(), "RightEarBlocky" to twitch, "LeftEarBlocky" to twitch.mirroredZ())
    })))

    fun humanoid(identifier: String, p: HumanoidProportions): GeoModel {
        val rig = Rig()
        rig.bone("root", null, v(0, 0, 0))

        val bodyOrigin = v(-p.bodyWidth / 2, p.hip, -p.bodyDepth / 2)
        val bodySize = v(p.bodyWidth, p.bodyHeight, p.bodyDepth)
        rig.bone("Body", "root", v(0, p.neck, 0), null, part(bodyOrigin, bodySize, BODY), part(bodyOrigin, bodySize, JACKET, 0.25))
        p.chestScale?.let { chest(rig, p, it) }

        rig.bone("Head", "root", v(0, p.neck, 0), null,
            part(v(-4, p.neck, -4), v(8, 8, 8), HEAD), part(v(-4, p.neck, -4), v(8, 8, 8), HAT, 0.5))
        ears(rig, p.headTop)
        if (p.ponytails) ponytails(rig, p.headTop)

        for (side in Side.entries) arm(rig, p, side)
        for (side in Side.entries) leg(rig, p, side)
        return GeoModel(identifier, rig)
    }

    /**
     * Arms: the parent bone is what the walk animation rotates. With a slim variant, Wide (XY) and Slim (XX)
     * children are toggled by the race's sex rules.
     */
    private fun arm(rig: Rig, p: HumanoidProportions, side: Side) {
        val s = side.sign
        val pivot = v(s * (p.bodyWidth / 2 + 1), p.neck - p.armPivotDrop, 0)
        val (skin, sleeve) = if (side == Side.Right) PlayerSheet.RIGHT_ARM to PlayerSheet.RIGHT_SLEEVE else PlayerSheet.LEFT_ARM to PlayerSheet.LEFT_SLEEVE
        fun cubes(width: Double): Array<Cube> {
            val inner = p.bodyWidth / 2 + p.shoulderGap
            val x0 = if (s < 0) -(inner + width) else inner
            val origin = v(x0, p.neck - p.armTopDrop - p.armLength, -p.armDepth / 2)
            val size = v(width, p.armLength, p.armDepth)
            return arrayOf(part(origin, size, skin), part(origin, size, sleeve, 0.25))
        }
        val slim = p.slimArmWidth
        if (slim == null) {
            rig.bone("${side}Arm", "root", pivot, null, *cubes(p.armWidth))
        } else {
            rig.bone("${side}Arm", "root", pivot)
            rig.bone("${side}ArmWide", "${side}Arm", pivot, null, *cubes(p.armWidth))
            rig.bone("${side}ArmSlim", "${side}Arm", pivot, null, *cubes(slim))
        }
    }

    private fun leg(rig: Rig, p: HumanoidProportions, side: Side) {
        val s = side.sign
        val (skin, pants) = if (side == Side.Right) PlayerSheet.RIGHT_LEG to PlayerSheet.RIGHT_PANTS else PlayerSheet.LEFT_LEG to PlayerSheet.LEFT_PANTS
        val x0 = if (s < 0) -p.legWidth + p.legInset else -p.legInset
        val origin = v(x0, 0, -p.legDepth / 2)
        val size = v(p.legWidth, p.legLength, p.legDepth)
        rig.bone("${side}Leg", "root", v(s * (p.legWidth / 2 - p.legInset), p.hip, 0), null,
            part(origin, size, skin), part(origin, size, pants, 0.25))
    }

    /**
     * Wildfire-style chest: two bones under a "Chest" group, each hinged where its top meets the chest and mostly
     * sunk into the torso (1px proud, 3px deep), inset from the torso's sides, so the race's tilt/spread/bounce never
     * opens a gap or pokes past the silhouette. Each face samples the half of the shirt/skin rows it sits over, so
     * clothes carry onto it without extra art.
     */
    private fun chest(rig: Rig, p: HumanoidProportions, scale: Double) {
        val front = -p.bodyDepth / 2
        rig.bone("Chest", "Body", v(0, p.neck - 3, front))
        val width = p.bodyWidth / 2 - 0.5
        val height = 4 * scale
        for (side in Side.entries) {
            val centre = side.sign * p.bodyWidth / 4
            val column = if (side == Side.Right) 0 else BODY.w / 2
            val origin = v(centre - width / 2, p.neck - 2.5 - height, front - 1)
            val size = v(width, height, 4)
            rig.bone("${side}Breast", "Chest", v(centre, p.neck - 2.5, front), null,
                Cube(origin, size, CubeUv.PerFace(chestFaces(BODY, column))),
                Cube(origin, size, CubeUv.PerFace(chestFaces(JACKET, column)), 0.25))
        }
    }

    /** The half ([column] 0 or 4) of the body's front, rows 3..7, on every face. */
    private fun chestFaces(region: Region, column: Int): Map<Face, FaceUv> {
        val x = region.u + region.d + column
        val top = region.v + region.d + 3
        return mapOf(
            Face.NORTH to FaceUv(x, top, 4, 4), Face.SOUTH to FaceUv(x, top, 4, 4),
            Face.UP to FaceUv(x, top, 4, 1), Face.DOWN to FaceUv(x, top + 3, 4, 1),
            Face.EAST to FaceUv(x, top, 1, 4), Face.WEST to FaceUv(x + 3, top, 1, 4),
        )
    }

    /**
     * Two ear styles toggled by the race: long pointed ears (three tapering cubes reaching outward, tilted up and
     * swept back) and short broad ones. Ear roots sit at mid-head height.
     */
    private fun ears(rig: Rig, headTop: Double) {
        val y = headTop - 4
        rig.bone("EarsRegular", "Head", v(0, headTop - 8, 0))
        rig.bone("RightEar", "EarsRegular", v(-4, y, 0.5), v(0, 30, -25),
            part(v(-6, y - 1, 0), v(2, 3, 1), PlayerSheet.EAR_RIGHT_BASE),
            part(v(-8, y, 0), v(2, 2, 1), PlayerSheet.EAR_RIGHT_MID),
            part(v(-9, y + 1, 0), v(1, 1, 1), PlayerSheet.EAR_RIGHT_TIP))
        rig.bone("LeftEar", "EarsRegular", v(4, y, 0.5), v(0, -30, 25),
            part(v(4, y - 1, 0), v(2, 3, 1), PlayerSheet.EAR_LEFT_BASE),
            part(v(6, y, 0), v(2, 2, 1), PlayerSheet.EAR_LEFT_MID),
            part(v(8, y + 1, 0), v(1, 1, 1), PlayerSheet.EAR_LEFT_TIP))
        rig.bone("EarsBlocky", "Head", v(0, headTop - 8, 0))
        rig.bone("RightEarBlocky", "EarsBlocky", v(-4, y, 0), v(0, 25, -10), part(v(-6, y - 1, -1), v(2, 3, 2), PlayerSheet.EAR_RIGHT_BLOCKY))
        rig.bone("LeftEarBlocky", "EarsBlocky", v(4, y, 0), v(0, -25, 10), part(v(4, y - 1, -1), v(2, 3, 2), PlayerSheet.EAR_LEFT_BLOCKY))
    }

    /**
     * Ponytail styles shown one at a time by the race's ponytail gene: high, low, braided and twin tails. Every cube
     * samples the shared ponytail strip; bind rotations tilt the tails away from the head and the race adds swing.
     */
    private fun ponytails(rig: Rig, top: Double) {
        val back = 4.0 // back of the head
        fun hair(origin: V3, size: V3) = part(origin, size, PlayerSheet.PONYTAIL)
        rig.bone("Ponytails", "Head", v(0, top - 8, 0))
        rig.bone("PonytailHigh", "Ponytails", v(0, top - 1, back + 0.5), v(22, 0, 0),
            hair(v(-1.5, top - 2, back), v(3, 2, 1.5)),
            hair(v(-1, top - 10, back + 0.5), v(2, 8, 2)))
        rig.bone("PonytailLow", "Ponytails", v(0, top - 6.5, back + 0.5), v(10, 0, 0),
            hair(v(-1.5, top - 7.5, back), v(3, 2, 1.5)),
            hair(v(-1, top - 15.5, back + 0.5), v(2, 8, 2)))
        rig.bone("PonytailBraid", "Ponytails", v(0, top - 4, back + 0.5), v(8, 0, 0),
            hair(v(-1.5, top - 5, back), v(3, 2, 1.5)),
            hair(v(-1.25, top - 9, back + 0.5), v(2.5, 4, 2)),
            hair(v(-1, top - 12.5, back + 0.6), v(2, 3.5, 1.8)),
            hair(v(-0.75, top - 16, back + 0.7), v(1.5, 3.5, 1.6)),
            hair(v(-1, top - 17, back + 0.5), v(2, 1, 2)))
        for (side in listOf(Side.Left, Side.Right)) {
            val s = side.sign
            rig.bone("PonytailTwin$side", "Ponytails", v(s * 3.5, top - 3, back - 0.5), v(10, 0, s * 12),
                hair(v(if (s > 0) 2.5 else -4.5, top - 4, back - 1.5), v(2, 2, 2)),
                hair(v(if (s > 0) 2.75 else -4.25, top - 11.5, back - 1), v(1.5, 7.5, 1.5)))
        }
    }
}
