package net.oraclehisty.rejecthumanity.assetgen

/** A point or size in model units (1 = one texture pixel = 1/16 block). Bedrock axes: y up, the model faces -z. */
data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun times(s: Double) = V3(x * s, y * s, z * s)
    fun toJson() = listOf(x, y, z)
}

fun v(x: Number, y: Number, z: Number) = V3(x.toDouble(), y.toDouble(), z.toDouble())

/** Cube faces, in the order Blockbench writes them. "north" is the front, "east" the model's right (-x) side. */
enum class Face(val key: String) {
    NORTH("north"), EAST("east"), SOUTH("south"), WEST("west"), UP("up"), DOWN("down");

    companion object {
        val SIDES = listOf(NORTH, SOUTH, EAST, WEST)
    }
}

/** A rectangle of texture pixels. */
data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * A box-UV texture region: the standard Minecraft layout of a [w]×[h]×[d] box whose top-left corner is ([u], [v]).
 * Orientation on the sheet: "up" ends at the front edge (its last rows), "east" ends at the front (last columns),
 * "west" starts at the front (first columns).
 */
data class Region(val u: Int, val v: Int, val w: Int, val h: Int, val d: Int) {
    val sheetWidth get() = 2 * w + 2 * d
    val sheetHeight get() = d + h

    fun rect(face: Face) = when (face) {
        Face.NORTH -> Rect(u + d, v + d, w, h)
        Face.EAST -> Rect(u, v + d, d, h)
        Face.SOUTH -> Rect(u + 2 * d + w, v + d, w, h)
        Face.WEST -> Rect(u + d + w, v + d, d, h)
        Face.UP -> Rect(u + d, v, w, d)
        Face.DOWN -> Rect(u + d + w, v, w, d)
    }

    /** Per-face UVs equivalent to this box layout; the bottom face is read upside down, as box UV does. */
    fun faceUvs(): Map<Face, FaceUv> = Face.entries.associateWith { face ->
        val r = rect(face)
        if (face == Face.DOWN) FaceUv(r.x, r.y + r.h, r.w, -r.h) else FaceUv(r.x, r.y, r.w, r.h)
    }

    fun matches(size: V3) = size.x == w.toDouble() && size.y == h.toDouble() && size.z == d.toDouble()
}

/** One face's texture rectangle; a negative size flips it. */
data class FaceUv(val u: Number, val v: Number, val w: Number, val h: Number) {
    fun toJson() = mapOf("uv" to listOf(u, v), "uv_size" to listOf(w, h))
}

sealed interface CubeUv {
    /** Plain box UV at (u, v): the region's size equals the cube's. */
    data class Box(val u: Int, val v: Int) : CubeUv

    data class PerFace(val faces: Map<Face, FaceUv>) : CubeUv
}

class Cube(val origin: V3, val size: V3, val uv: CubeUv, val inflate: Double = 0.0) {
    fun toJson(): Map<String, Any> = buildMap {
        put("origin", origin.toJson())
        put("size", this@Cube.size.toJson())
        put("uv", when (uv) {
            is CubeUv.Box -> listOf(uv.u, uv.v)
            is CubeUv.PerFace -> Face.entries.filter { it in uv.faces }.associate { it.key to uv.faces.getValue(it).toJson() }
        })
        if (inflate != 0.0) put("inflate", inflate)
    }
}

/**
 * A cube textured from [region]: every face samples the region's matching face, stretched or squeezed to fit (so
 * slimmer or shorter parts reuse the same art). With [boxUv], a cube whose size matches the region is written as
 * plain box UV instead (same result, simpler to edit in Blockbench). [mirrorSides] swaps the east/west faces, so a
 * region shared by left and right parts keeps its "inner" side facing inward on both.
 */
fun part(origin: V3, size: V3, region: Region, inflate: Double = 0.0, mirrorSides: Boolean = false, boxUv: Boolean = true): Cube {
    if (boxUv && region.matches(size) && !mirrorSides) return Cube(origin, size, CubeUv.Box(region.u, region.v), inflate)
    val faces = region.faceUvs().toMutableMap()
    if (mirrorSides) {
        val east = faces.getValue(Face.EAST)
        faces[Face.EAST] = faces.getValue(Face.WEST)
        faces[Face.WEST] = east
    }
    return Cube(origin, size, CubeUv.PerFace(faces), inflate)
}

/** Bind rotations are in Bedrock degrees: GeckoLib negates X and Y on load, so X > 0 swings a part's far end back. */
class Bone(val name: String, val parent: String?, val pivot: V3, val rotation: V3?, val cubes: List<Cube>) {
    fun toJson(): Map<String, Any> = buildMap {
        put("name", name)
        if (parent != null) put("parent", parent)
        put("pivot", pivot.toJson())
        if (rotation != null) put("rotation", rotation.toJson())
        if (cubes.isNotEmpty()) put("cubes", cubes.map(Cube::toJson))
    }
}

/** Collects bones in order; parents must be added before their children. */
class Rig {
    val bones = mutableListOf<Bone>()

    fun bone(name: String, parent: String?, pivot: V3, rotation: V3? = null, vararg cubes: Cube) {
        require(parent == null || bones.any { it.name == parent }) { "Bone $name: parent $parent not defined yet" }
        require(bones.none { it.name == name }) { "Duplicate bone $name" }
        bones += Bone(name, parent, pivot, rotation, cubes.toList())
    }
}

class GeoModel(
    val identifier: String,
    val rig: Rig,
    val textureWidth: Int = 64,
    val textureHeight: Int = 64,
    /** Culling box in blocks: width, height, and its vertical centre. */
    val visibleBounds: Triple<Double, Double, Double> = Triple(3.0, 3.5, 1.25),
) {
    fun toJson() = mapOf(
        "format_version" to "1.12.0",
        "minecraft:geometry" to listOf(mapOf(
            "description" to mapOf(
                "identifier" to identifier,
                "texture_width" to textureWidth,
                "texture_height" to textureHeight,
                "visible_bounds_width" to visibleBounds.first,
                "visible_bounds_height" to visibleBounds.second,
                "visible_bounds_offset" to listOf(0, visibleBounds.third, 0),
            ),
            "bones" to rig.bones.map(Bone::toJson),
        )),
    )
}

/** A keyframed rotation: time (seconds) -> Bedrock degrees, offsets from the bind pose. */
class Keyframes(val frames: List<Pair<Double, V3>>) {
    fun mirroredZ() = Keyframes(frames.map { (t, r) -> t to V3(r.x, r.y, -r.z) })
    fun toJson() = mapOf("rotation" to frames.associate { (t, r) -> t.toString() to r.toJson() })
}

class AnimationFile(private val animations: Map<String, Pair<Double, Map<String, Keyframes>>>) {
    fun toJson() = mapOf(
        "format_version" to "1.8.0",
        "animations" to animations.mapValues { (_, anim) ->
            mapOf("animation_length" to anim.first, "bones" to anim.second.mapValues { it.value.toJson() })
        },
    )
}
