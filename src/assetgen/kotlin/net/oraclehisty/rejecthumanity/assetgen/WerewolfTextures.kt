package net.oraclehisty.rejecthumanity.assetgen

import net.oraclehisty.rejecthumanity.assetgen.Face.DOWN
import net.oraclehisty.rejecthumanity.assetgen.Face.EAST
import net.oraclehisty.rejecthumanity.assetgen.Face.NORTH
import net.oraclehisty.rejecthumanity.assetgen.Face.SOUTH
import net.oraclehisty.rejecthumanity.assetgen.Face.UP
import net.oraclehisty.rejecthumanity.assetgen.Face.WEST

/**
 * Placeholder werewolf layers on the packed werewolf sheet. Greyscale layers are tinted by the race (fur, patterns,
 * markings, eyes); nose, inner ears and clothes are coloured. Painting by part and face keeps them aligned whatever
 * the packing; see [Region] for which way each face runs.
 */
object WerewolfTextures {
    private fun sheet(part: String) = WerewolfSheet[part]

    private fun layer(paint: Canvas.() -> Unit) = Canvas().apply(paint)

    private fun Canvas.paint(
        part: String,
        vararg faces: Face,
        rows: Pair<Int, Int>? = null,
        cols: Pair<Int, Int>? = null,
        color: Int = WHITE,
        where: ((Int, Int, Int, Int) -> Boolean)? = null,
    ) = paint(sheet(part), if (faces.isEmpty()) Face.entries else faces.toList(), rows, cols, color, where)

    fun all(): Map<String, Canvas> = buildMap {
        // Base fur, tinted by coat pigment: lit from above, with grain.
        put("fur", layer {
            for ((name, region) in WerewolfSheet.regions) {
                if (name == "nose") continue
                for (face in Face.entries) {
                    val r = region.rect(face)
                    val shade = when (face) { NORTH, UP -> 235; DOWN -> 205; else -> 222 }
                    for (y in 0 until r.h) for (x in 0 until r.w) {
                        this[r.x + x, r.y + y] = grey((shade + noise(r.x + x, r.y + y)).coerceIn(0, 255))
                    }
                }
            }
        })

        // Pale underside, alpha from the countershade gene.
        put("countershade", layer {
            paint("body", NORTH, rows = 4 to 12)
            paint("jaw")
            paint("muzzle", DOWN, EAST, WEST, rows = 2 to 3)
            paint("muzzle", DOWN)
            paint("head", NORTH, rows = 5 to 7)
            paint("ruff", NORTH)
            paint("thigh", NORTH)
            paint("arm", NORTH, rows = 4 to 12)
            for (part in listOf("tail", "tail_bush", "tail_tip")) paint(part, DOWN)
        })

        // Coat patterns, tinted a darker shade of the coat.
        put("pattern/agouti", layer { // wolf-like darker top line, ear tips, tail tip
            for (part in listOf("head", "muzzle", "ruff", "body", "tail", "tail_bush")) paint(part, UP)
            paint("body", SOUTH, cols = 2 to 6)
            paint("ruff", SOUTH)
            paint("head", SOUTH, EAST, WEST, rows = 0 to 2)
            paint("ear_tip"); paint("tail_tip")
            for (part in listOf("tail", "tail_bush")) paint(part, SOUTH, EAST, WEST, rows = 0 to 1)
        })
        put("pattern/saddle", layer { // black-and-tan style back blanket
            paint("body", SOUTH)
            paint("body", EAST, WEST, rows = 0 to 8)
            paint("body", UP)
            paint("ruff", SOUTH, UP)
            paint("thigh", SOUTH, EAST, WEST, rows = 0 to 3)
            for (part in listOf("tail", "tail_bush", "tail_tip")) paint(part, UP, EAST, WEST, SOUTH)
        })
        put("pattern/masked", layer { // dark face mask, ears and tail tip
            paint("head", NORTH, rows = 1 to 4)
            paint("head", EAST, rows = 1 to 4, cols = -4 to 0) // east's front edge is its last column
            paint("head", WEST, rows = 1 to 4, cols = 0 to 4)  // west's is its first
            paint("muzzle", UP)
            paint("ear"); paint("ear_tip"); paint("ear_floppy"); paint("tail_tip")
        })

        // White markings.
        put("markings/socks", layer { // white paws, lower legs and hands
            paint("paw"); paint("shin", *Face.SIDES.toTypedArray(), rows = -3 to 0)
            paint("arm", *Face.SIDES.toTypedArray(), rows = -3 to 0); paint("arm", DOWN)
        })
        put("markings/blaze", layer { // stripe up the muzzle and forehead, white chest
            paint("muzzle", UP, cols = 1 to 3)
            paint("head", NORTH, cols = 3 to 4)
            paint("head", UP, cols = 3 to 4, rows = -3 to 0) // the top face's last rows meet the forehead
            paint("body", NORTH, rows = 0 to 6, where = { x, y, _, _ -> kotlin.math.abs(x - 3.5) <= 3.5 - y * 0.55 })
            paint("ruff", NORTH, cols = 2 to 7)
        })
        put("markings/piebald", layer { // irregular white patches
            paint("body", EAST, rows = 5 to 10); paint("body", SOUTH, rows = 6 to 11, cols = 0 to 5)
            paint("thigh", WEST, NORTH, rows = 1 to 5); paint("head", EAST, rows = 2 to 6, cols = 1 to 5)
            paint("tail_bush", *Face.SIDES.toTypedArray(), rows = 0 to 3); paint("arm", WEST, rows = 2 to 7)
        })

        // Fixed-colour details.
        val noseColor = rgb(30, 26, 26)
        val lipColor = rgb(46, 36, 36)
        put("nose", layer {
            paint("nose", color = noseColor)
            paint("jaw", NORTH, rows = 0 to 1, color = lipColor)
            paint("jaw", UP, rows = -1 to 0, color = lipColor) // lip line along the front edge
            // Also on the snout tip, for models without a nose block (pups); hidden behind the adult's nose.
            paint("muzzle", NORTH, rows = 0 to 1, cols = 1 to 3, color = noseColor)
        })
        val pink = rgb(200, 140, 140)
        put("inner_ear", layer {
            paint("ear", NORTH, rows = 1 to 3, cols = 1 to 2, color = pink)
            paint("ear_floppy", WEST, rows = 1 to 4, cols = 1 to 3, color = pink) // inner side only
        })

        // Eyes (tinted, hidden while blinking).
        put("eye_left", layer { paint("head", NORTH, rows = 2 to 3, cols = 1 to 2) })
        put("eye_right", layer { paint("head", NORTH, rows = 2 to 3, cols = 5 to 6) })

        // Clothes.
        val leather = rgb(92, 64, 42)
        val cloth = rgb(110, 88, 62)
        val hem = rgb(80, 64, 46)
        val ragged = { x: Int, y: Int, _: Int, h: Int -> !(y == h - 1 && x % 2 == 0) } // torn hem
        for (sex in listOf("x", "y")) {
            put("clothes/$sex", layer {
                paint("thigh", color = cloth, where = ragged)
                paint("body", *Face.SIDES.toTypedArray(), rows = -2 to 0, color = leather) // belt
                if (sex == "x") {
                    paint("body", *Face.SIDES.toTypedArray(), rows = 3 to 7, color = cloth) // chest wrap (the chest samples these rows)
                    paint("body", *Face.SIDES.toTypedArray(), rows = 3 to 4, color = hem)
                }
            })
        }
    }
}
