package net.oraclehisty.rejecthumanity.assetgen

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
fun rgb(r: Int, g: Int, b: Int) = argb(255, r, g, b)
fun grey(v: Int, a: Int = 255) = argb(a, v, v, v)

const val WHITE = -1 // 0xFFFFFFFF

/** A texture being painted. Greyscale art is tinted by the race at runtime; transparent pixels aren't drawn. */
class Canvas(val width: Int = 64, val height: Int = 64) {
    private val pixels = IntArray(width * height)

    operator fun get(x: Int, y: Int) = pixels[y * width + x]

    operator fun set(x: Int, y: Int, color: Int) {
        pixels[y * width + x] = color
    }

    fun isSet(x: Int, y: Int) = (this[x, y] ushr 24) != 0

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y until y + h) for (xx in x until x + w) this[xx, yy] = color
    }

    /**
     * Fills faces of a box-UV [region]. [rows]/[cols] are (start, end) within each face; a negative start, or an
     * end of 0 or less, counts from the far edge, so (-3, 0) is the last three rows. [where] (x, y, faceW, faceH)
     * can carve shapes.
     */
    fun paint(
        region: Region,
        faces: Collection<Face> = Face.entries,
        rows: Pair<Int, Int>? = null,
        cols: Pair<Int, Int>? = null,
        color: Int = WHITE,
        where: ((Int, Int, Int, Int) -> Boolean)? = null,
    ) {
        for (face in faces) {
            val r = region.rect(face)
            val (r0, r1) = span(rows, r.h)
            val (c0, c1) = span(cols, r.w)
            for (y in r0.coerceAtLeast(0) until r1.coerceAtMost(r.h)) {
                for (x in c0.coerceAtLeast(0) until c1.coerceAtMost(r.w)) {
                    if (where == null || where(x, y, r.w, r.h)) this[r.x + x, r.y + y] = color
                }
            }
        }
    }

    private fun span(range: Pair<Int, Int>?, size: Int): Pair<Int, Int> {
        if (range == null) return 0 to size
        val (start, end) = range
        return (if (start < 0) start + size else start) to (if (end <= 0) end + size else end)
    }

    /**
     * Fills a whole box-UV region the way clothing and skin are laid out: [color] on the front, back and top,
     * [sideColor] on the sides, [bottomColor] underneath. [rows] limits the four sides; the top/bottom are only
     * painted when the rows reach that edge.
     */
    fun fillBox(region: Region, color: Int, rows: Pair<Int, Int>? = null, sideColor: Int? = null, bottomColor: Int? = null) {
        val (r0, r1) = rows ?: (0 to region.h)
        if (r0 == 0) paint(region, listOf(Face.UP), color = color)
        if (r1 == region.h) paint(region, listOf(Face.DOWN), color = bottomColor ?: color)
        paint(region, listOf(Face.NORTH, Face.SOUTH), rows = r0 to r1, color = color)
        paint(region, listOf(Face.EAST, Face.WEST), rows = r0 to r1, color = sideColor ?: color)
    }

    /** Writes the PNG unless [file] already holds the same pixels, so regenerating doesn't churn unchanged files. Returns whether it wrote. */
    fun save(file: File): Boolean {
        if (file.exists() && samePixels(file)) return false
        file.parentFile.mkdirs()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, pixels, 0, width)
        ImageIO.write(image, "png", file)
        return true
    }

    private fun samePixels(file: File): Boolean {
        val existing = ImageIO.read(file) ?: return false
        if (existing.width != width || existing.height != height) return false
        val old = existing.getRGB(0, 0, width, height, null, 0, width)
        // Fully transparent pixels are equal whatever colour they carry.
        return old.indices.all { i -> old[i] == pixels[i] || (old[i] ushr 24 == 0 && pixels[i] ushr 24 == 0) }
    }
}

/** Deterministic per-pixel noise in -8..8, for fur and skin grain. */
fun noise(x: Int, y: Int): Int {
    var n = (x.toLong() * 374761393L + y.toLong() * 668265263L) and 0xFFFFFFFFL
    n = ((n xor (n shr 13)) * 1274126177L) and 0xFFFFFFFFL
    return ((n shr 8) % 17).toInt() - 8
}
