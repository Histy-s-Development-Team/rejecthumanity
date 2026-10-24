package net.oraclehisty.rejecthumanity.assetgen

import kotlin.math.cos
import kotlin.math.sin

/**
 * The werewolf texture sheet: one box-UV region per canine part, shelf-packed (tallest first) into 64×64. Paired
 * parts (arms, legs, ears) share a region, so fur patterns are symmetric and the sheet stays small. Teens and pups
 * sample the same regions, stretched to their own part sizes, so one set of fur art covers every life stage.
 */
object WerewolfSheet {
    private val SIZES = linkedMapOf(
        "arm" to Triple(4, 12, 4), "body" to Triple(8, 12, 4), "head" to Triple(7, 7, 7), "thigh" to Triple(4, 6, 5),
        "ruff" to Triple(9, 4, 6), "shin" to Triple(3, 6, 3), "tail" to Triple(3, 3, 5), "tail_bush" to Triple(4, 4, 4),
        "muzzle" to Triple(4, 3, 4), "paw" to Triple(4, 2, 5), "ear_floppy" to Triple(1, 4, 3), "tail_tip" to Triple(2, 2, 3),
        "ear" to Triple(3, 3, 1), "jaw" to Triple(4, 1, 3), "ear_tip" to Triple(2, 2, 1), "nose" to Triple(2, 1, 1),
    )

    val regions: Map<String, Region> = pack()

    operator fun get(part: String) = regions.getValue(part)

    private fun pack(): Map<String, Region> {
        val order = SIZES.entries.sortedWith(compareBy({ -(it.value.third + it.value.second) }, { -(2 * it.value.first + 2 * it.value.third) }))
        val placed = LinkedHashMap<String, Region>()
        var x = 0
        var y = 0
        var shelf = 0
        for ((name, size) in order) {
            val (w, h, d) = size
            val region = Region(0, 0, w, h, d)
            if (x + region.sheetWidth > 64) {
                x = 0
                y += shelf
                shelf = 0
            }
            check(y + region.sheetHeight <= 64) { "Werewolf UV sheet overflows at $name" }
            placed[name] = Region(x, y, w, h, d)
            x += region.sheetWidth
            shelf = maxOf(shelf, region.sheetHeight)
        }
        return SIZES.keys.associateWith { placed.getValue(it) }
    }
}

/**
 * Proportions of the werewolf rig: humanoid bone names (so the shared walk works), canine head, upright/folded or
 * floppy ears, neck ruff, digitigrade legs and a three-part tail. Sizes are (width, height, depth) in model units.
 */
data class WerewolfProportions(
    val hip: Double,
    val body: V3,
    val ruff: V3,
    val head: Double,
    val muzzle: V3,
    val nose: V3,
    val jaw: V3,
    val ear: V3,
    val earTip: V3,
    val earFloppy: V3,
    val arm: V3,
    val thigh: V3,
    val shin: V3,
    /** Paw width and length; its height is solved so the bent leg stands on the ground. */
    val pawWidth: Double,
    val pawLength: Double,
    val tail: V3,
    val tailMid: V3,
    val tailTip: V3,
    val chestScale: Double,
) {
    val neck get() = hip + body.y

    companion object {
        val ADULT = WerewolfProportions(
            hip = 13.0, body = v(8, 12, 4), ruff = v(9, 4, 6), head = 7.0,
            muzzle = v(4, 3, 4), nose = v(2, 1, 1), jaw = v(3.5, 1, 3.5),
            ear = v(3, 3, 1), earTip = v(2, 2, 1), earFloppy = v(1, 4, 3),
            arm = v(4, 12, 4), thigh = v(4, 6, 5), shin = v(3, 6.5, 3), pawWidth = 4.0, pawLength = 5.0,
            tail = v(3, 3, 5), tailMid = v(4, 4, 4), tailTip = v(2, 2, 3), chestScale = 1.0,
        )

        /**
         * Lanky and half-grown: narrower and shorter in body and limbs, a smaller ruff and shorter muzzle, but the
         * same size head and ears as an adult, which read as big for the body.
         */
        val TEEN = WerewolfProportions(
            hip = 12.0, body = v(7, 11, 3.5), ruff = v(8, 3, 5), head = 7.0,
            muzzle = v(3.5, 2.5, 3), nose = v(1.5, 1, 1), jaw = v(3, 1, 2.5),
            ear = v(3, 3.5, 1), earTip = v(2, 2, 1), earFloppy = v(1, 4, 3),
            arm = v(3.5, 11, 3.5), thigh = v(3.5, 5.5, 4.5), shin = v(2.5, 6, 2.5), pawWidth = 3.5, pawLength = 4.5,
            tail = v(2.5, 2.5, 4.5), tailMid = v(3.5, 3.5, 3.5), tailTip = v(2, 2, 2.5), chestScale = 0.75,
        )
    }
}

object WerewolfModels {
    /** A furred part on the werewolf sheet; always written with explicit per-face UVs (as approved in game). */
    private fun fur(origin: V3, size: V3, region: Region, mirrorSides: Boolean = false): Cube =
        part(origin, size, region, mirrorSides = mirrorSides, boxUv = false)

    /** Digitigrade leg pose (Bedrock degrees): thigh angled forward, shin back, paw flat. */
    private const val THIGH_ANGLE = -20.0
    private const val SHIN_ANGLE = 45.0
    private const val PAW_ANGLE = -(THIGH_ANGLE + SHIN_ANGLE)

    fun all() = mapOf(
        "werewolf" to werewolf("geometry.rejecthumanity.werewolf", WerewolfProportions.ADULT),
        "werewolf_teen" to werewolf("geometry.rejecthumanity.werewolf_teen", WerewolfProportions.TEEN),
        "werewolf_pup" to pup(),
    )

    val animations = AnimationFile(mapOf("ear_twitch" to (0.45 to run {
        val twitch = Keyframes(listOf(
            0.0 to v(0, 0, 0), 0.08 to v(-8, 0, 12), 0.18 to v(3, 0, -4), 0.3 to v(-2, 0, 5), 0.45 to v(0, 0, 0),
        ))
        mapOf("RightEar" to twitch, "LeftEar" to twitch.mirroredZ(), "RightEarFloppy" to twitch, "LeftEarFloppy" to twitch.mirroredZ())
    })))

    fun werewolf(identifier: String, p: WerewolfProportions): GeoModel {
        val rig = Rig()
        val sheet = WerewolfSheet
        val hip = p.hip
        val neck = p.neck
        val back = p.body.z / 2

        rig.bone("root", null, v(0, 0, 0))
        rig.bone("Body", "root", v(0, neck, 0), null, fur(v(-p.body.x / 2, hip, -back), p.body, sheet["body"]))
        rig.bone("Ruff", "Body", v(0, neck - 2, 0), null,
            fur(v(-p.ruff.x / 2, neck + 0.5 - p.ruff.y, -p.ruff.z / 2), p.ruff, sheet["ruff"]))
        chest(rig, p)

        head(rig, p)
        for (side in Side.entries) {
            val s = side.sign
            val inner = p.body.x / 2
            rig.bone("${side}Arm", "root", v(s * (inner + 1), neck - 2, 0), null,
                fur(v(if (s < 0) -(inner + p.arm.x) else inner, neck - 1 - p.arm.y, -p.arm.z / 2), p.arm, sheet["arm"]))
        }
        for (side in Side.entries) leg(rig, p, side)
        tail(rig, p)
        return GeoModel(identifier, rig)
    }

    private fun head(rig: Rig, p: WerewolfProportions) {
        val sheet = WerewolfSheet
        val h = p.neck            // head bottom
        val half = p.head / 2
        val front = -half
        rig.bone("Head", "root", v(0, h, 0), null, fur(v(-half, h, -half), v(p.head, p.head, p.head), sheet["head"]))
        rig.bone("Muzzle", "Head", v(0, h + 1.5, front), null,
            fur(v(-p.muzzle.x / 2, h + 1, front - p.muzzle.z), p.muzzle, sheet["muzzle"]),
            fur(v(-p.nose.x / 2, h + 1 + p.muzzle.y - p.nose.y, front - p.muzzle.z - 0.5), p.nose, sheet["nose"]))
        rig.bone("Jaw", "Head", v(0, h + 1, front), null, fur(v(-p.jaw.x / 2, h, front - p.jaw.z), p.jaw, sheet["jaw"]))

        // Upright ears: base + a separate tip bone, so the race can fold the tip (half-prick ears) and let it bob.
        // Rooted 1.5px into the skull so they rise from the head rather than perching on top of it.
        val top = h + p.head
        val earY = top - 1.5
        val earX = half - 1
        rig.bone("EarsErect", "Head", v(0, top, 0))
        for (side in listOf(Side.Right, Side.Left)) {
            val s = side.sign
            val x = s * earX
            rig.bone("${side}Ear", "EarsErect", v(x, earY, 0.5), v(7, 0, -s * 10), fur(v(x - p.ear.x / 2, earY, 0), p.ear, sheet["ear"]))
            rig.bone("${side}EarTip", "${side}Ear", v(x, earY + p.ear.y, 0.5), null,
                fur(v(x - p.earTip.x / 2, earY + p.ear.y, 0), p.earTip, sheet["ear_tip"]))
        }

        // Floppy ears hang from the top corners of the head and flare outward, clear of the skull. The region's west
        // face is the inner (head-facing) side: right ear as-is, left ear mirrored.
        rig.bone("EarsFloppy", "Head", v(0, top, 0))
        for (side in listOf(Side.Right, Side.Left)) {
            val s = side.sign
            val x0 = if (s < 0) -half - 0.5 - p.earFloppy.x else half + 0.5
            rig.bone("${side}EarFloppy", "EarsFloppy", v(s * half, top - 0.5, 0), v(0, 0, -s * 12),
                fur(v(x0, top - p.earFloppy.y, -p.earFloppy.z / 2), p.earFloppy, sheet["ear_floppy"], mirrorSides = side == Side.Left))
        }
    }

    /**
     * Digitigrade chain under each hip: thigh angled forward, shin back, paw flat. The paw's height is solved with
     * forward kinematics so its sole rests on y = 0 whatever the segment lengths.
     */
    private fun leg(rig: Rig, p: WerewolfProportions, side: Side) {
        val sheet = WerewolfSheet
        val x = side.sign * p.body.x / 4
        val kneeY = p.hip - p.thigh.y + 0.5
        val shinBottom = kneeY - p.shin.y
        val ankleY = shinBottom + 0.5
        val pawHeight = groundedAnkleHeight(p.hip, kneeY, ankleY)

        rig.bone("${side}Leg", "root", v(x, p.hip, 0), v(THIGH_ANGLE, 0, 0),
            fur(v(x - p.thigh.x / 2, p.hip - p.thigh.y, -p.thigh.z / 2), p.thigh, sheet["thigh"]))
        rig.bone("${side}Shin", "${side}Leg", v(x, kneeY, 0), v(SHIN_ANGLE, 0, 0),
            fur(v(x - p.shin.x / 2, shinBottom, -p.shin.z / 2), p.shin, sheet["shin"]))
        rig.bone("${side}Paw", "${side}Shin", v(x, ankleY, 0), v(PAW_ANGLE, 0, 0),
            fur(v(x - p.pawWidth / 2, ankleY - pawHeight, -(p.pawLength - 0.5)), v(p.pawWidth, pawHeight, p.pawLength), sheet["paw"]))
    }

    /**
     * Height of the ankle above the ground once the thigh and shin bind rotations are applied (the paw's net rotation
     * is zero, so its sole sits that far below the ankle). A Bedrock X rotation of θ swings a point below its pivot
     * back by sin θ: (y, z) -> (y cos θ + z sin θ, -y sin θ + z cos θ).
     */
    private fun groundedAnkleHeight(hip: Double, kneeY: Double, ankleY: Double): Double {
        fun rotate(y: Double, z: Double, degrees: Double): Pair<Double, Double> {
            val a = Math.toRadians(degrees)
            return (y * cos(a) + z * sin(a)) to (-y * sin(a) + z * cos(a))
        }
        val (kneeDy, _) = rotate(kneeY - hip, 0.0, THIGH_ANGLE)
        val (ankleDy, _) = rotate(ankleY - kneeY, 0.0, THIGH_ANGLE + SHIN_ANGLE)
        return Math.round((hip + kneeDy + ankleDy) * 10) / 10.0
    }

    private fun chest(rig: Rig, p: WerewolfProportions) {
        val front = -p.body.z / 2
        val neck = p.neck
        rig.bone("Chest", "Body", v(0, neck - 2.5, front))
        val body = WerewolfSheet["body"]
        val width = p.body.x / 2 - 0.5
        val height = 4 * p.chestScale
        for (side in Side.entries) {
            val centre = side.sign * p.body.x / 4
            val column = if (side == Side.Right) 0 else body.w / 2
            val x = body.u + body.d + column
            val top = body.v + body.d + 3
            val faces = mapOf(
                Face.NORTH to FaceUv(x, top, 4, 4), Face.SOUTH to FaceUv(x, top, 4, 4),
                Face.UP to FaceUv(x, top, 4, 1), Face.DOWN to FaceUv(x, top + 3, 4, 1),
                Face.EAST to FaceUv(x, top, 1, 4), Face.WEST to FaceUv(x + 3, top, 1, 4),
            )
            rig.bone("${side}Breast", "Chest", v(centre, neck - 2.5, front), null,
                Cube(v(centre - width / 2, neck - 2.5 - height, front - 1), v(width, height, 4), CubeUv.PerFace(faces)))
        }
    }

    /** Rooted at the base of the spine and drooping; the race file curls, bushes and wags it. */
    private fun tail(rig: Rig, p: WerewolfProportions) {
        val sheet = WerewolfSheet
        val axis = p.hip + 1.5
        val z0 = p.body.z / 2
        val z1 = z0 + p.tail.z
        val z2 = z1 + p.tailMid.z
        fun segment(size: V3, z: Double, region: String) = fur(v(-size.x / 2, axis - size.y / 2, z), size, sheet[region])
        rig.bone("Tail", "Body", v(0, axis, z0), v(-35, 0, 0), segment(p.tail, z0, "tail"))
        rig.bone("TailMid", "Tail", v(0, axis, z1), v(-15, 0, 0), segment(p.tailMid, z1, "tail_bush"))
        rig.bone("TailTip", "TailMid", v(0, axis, z2), v(-10, 0, 0), segment(p.tailTip, z2, "tail_tip"))
    }

    /**
     * Werewolf babies: a simple near-vanilla wolf pup (boxy head and plain snout, a mane over the shoulders,
     * single-block ears, stick legs, a short tail carried up). Same bone names as the adult (front legs are
     * RightArm/LeftArm), so coat layers, bone rules and the shared walk (which becomes a trot) apply unchanged.
     */
    fun pup(): GeoModel {
        val sheet = WerewolfSheet
        val belly = 5.0
        val back = 10.0
        val front = -3.0
        val rear = 5.0
        val headY = 8.0
        val headZ = -8.0
        val rig = Rig()
        rig.bone("root", null, v(0, 0, 0))
        rig.bone("Body", "root", v(0, back, front), null, pupTorso(v(-3, belly, front), v(6, back - belly, rear - front)))
        rig.bone("Ruff", "Body", v(0, back, front), null, fur(v(-3.5, belly - 0.5, front - 1), v(7, 6, 4), sheet["ruff"]))

        rig.bone("Head", "root", v(0, back, front - 1), null, fur(v(-3, headY, headZ), v(6, 5, 5), sheet["head"]))
        rig.bone("Muzzle", "Head", v(0, headY + 1, headZ), null, fur(v(-1.5, headY, headZ - 3), v(3, 2, 3), sheet["muzzle"]))

        rig.bone("EarsErect", "Head", v(0, headY + 5, headZ + 2.5))
        rig.bone("EarsFloppy", "Head", v(0, headY + 5, headZ + 2.5))
        for (side in listOf(Side.Right, Side.Left)) {
            val s = side.sign
            rig.bone("${side}Ear", "EarsErect", v(s * 2, headY + 5, headZ + 2.5), v(7, 0, -s * 8),
                fur(v(if (s < 0) -3 else 1, headY + 5, headZ + 2), v(2, 2, 1), sheet["ear"]))
        }
        for (side in listOf(Side.Right, Side.Left)) {
            val s = side.sign
            rig.bone("${side}EarFloppy", "EarsFloppy", v(s * 3, headY + 4.5, headZ + 2.5), v(0, 0, -s * 12),
                fur(v(if (s < 0) -4 else 3, headY + 2, headZ + 1.5), v(1, 2.5, 2), sheet["ear_floppy"], mirrorSides = side == Side.Left))
        }

        // Stick legs. Front legs use the arm fur (socks reach the forepaws), hind legs the shin fur.
        for ((name, z) in listOf("Arm" to front + 1, "Leg" to rear - 1)) {
            for (side in Side.entries) {
                val s = side.sign
                rig.bone("$side$name", "root", v(s * 1.5, belly + 1, z), null,
                    fur(v(if (s < 0) -2.5 else 0.5, 0, z - 1), v(2, belly + 1, 2), sheet[if (name == "Arm") "arm" else "shin"]))
            }
        }

        // Short tail carried up and out (X > 0 lifts a backward-pointing part).
        rig.bone("Tail", "Body", v(0, back - 1, rear), v(30, 0, 0), fur(v(-1, back - 2, rear), v(2, 2, 3), sheet["tail"]))
        rig.bone("TailMid", "Tail", v(0, back - 1, rear + 3), null, fur(v(-1.25, back - 2.25, rear + 3), v(2.5, 2.5, 3), sheet["tail_bush"]))
        return GeoModel("geometry.rejecthumanity.werewolf_pup", rig, visibleBounds = Triple(2.0, 2.0, 0.75))
    }

    /**
     * Horizontal torso dressed with the upright body's fur: back = body's back (saddle/agouti stripe run along the
     * spine, neck end forward), belly = body's front (pale countershading), sides = body's sides.
     */
    private fun pupTorso(origin: V3, size: V3): Cube {
        val r = WerewolfSheet["body"]
        val backX = r.u + 2 * r.d + r.w
        val frontX = r.u + r.d
        val y = r.v + r.d
        return Cube(origin, size, CubeUv.PerFace(mapOf(
            Face.UP to FaceUv(backX, y + r.h, r.w, -r.h),           // flipped so the neck end meets the chest
            Face.DOWN to FaceUv(frontX, y, r.w, r.h),
            Face.NORTH to FaceUv(frontX, y, r.w, r.h / 2),           // chest: upper half of the front
            Face.SOUTH to FaceUv(backX, y + r.h / 2, r.w, r.h / 2),  // rump: lower half of the back
            Face.EAST to FaceUv(r.u, y, r.d, r.h),
            Face.WEST to FaceUv(r.u + r.d + r.w, y, r.d, r.h),
        )))
    }
}
