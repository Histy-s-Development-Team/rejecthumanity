package net.oraclehisty.rejecthumanity.assetgen

import net.oraclehisty.rejecthumanity.assetgen.PlayerSheet.JACKET
import net.oraclehisty.rejecthumanity.assetgen.PlayerSheet.PANTS
import net.oraclehisty.rejecthumanity.assetgen.PlayerSheet.SLEEVES

/**
 * Placeholder layers for the elf race (and the Drow, who share the elf model): simple pixel marks so every gene is
 * visible in game, meant to be repainted. Greyscale where the race tints the layer; coloured where it doesn't.
 */
object ElfTextures {
    /** A point on the 8×8 front of the head: (column, row) from the top-left. */
    private fun Canvas.face(vararg points: Pair<Int, Int>, color: Int = WHITE) {
        for ((fx, fy) in points) this[8 + fx, 8 + fy] = color
    }

    private fun layer(paint: Canvas.() -> Unit) = Canvas().apply(paint)

    fun all(): Map<String, Canvas> = buildMap {
        put("skin", layer {
            for (r in PlayerSheet.SKIN_PARTS) fillBox(r, grey(250), sideColor = grey(232), bottomColor = grey(215))
            for (r in PlayerSheet.EAR_PARTS) fillBox(r, grey(242), sideColor = grey(228), bottomColor = grey(215))
            face(3 to 5, 4 to 5, color = grey(228)) // nose shading
        })

        // Eyes: whites are untinted; irises are tinted by eye colour and hidden while blinking.
        put("eye_whites", layer { face(1 to 4, 6 to 4, color = rgb(245, 245, 245)) })
        put("eye_left", layer { face(2 to 4) })
        put("eye_right", layer { face(5 to 4) })

        // Face details.
        put("eyelashes", layer { face(0 to 4, 0 to 3, 7 to 4, 7 to 3) }) // outer corners, flicked up
        put("lips", layer { face(3 to 6, 4 to 6) })
        put("blush", layer { face(0 to 5, 1 to 5, 6 to 5, 7 to 5, 0 to 6, 7 to 6) })
        put("under_eye", layer { face(1 to 5, 2 to 5, 5 to 5, 6 to 5) })

        val freckles = arrayOf(1 to 5, 3 to 5, 4 to 5, 6 to 5, 2 to 6, 5 to 6, 2 to 3, 5 to 3)
        val minimal = arrayOf(2 to 5, 4 to 5, 5 to 5)
        val mole = arrayOf(6 to 6)
        for ((name, spots, moles) in listOf(
            Triple("minimal_freckles", minimal, emptyArray<Pair<Int, Int>>()),
            Triple("freckles", freckles, emptyArray<Pair<Int, Int>>()),
            Triple("face_mole", emptyArray<Pair<Int, Int>>(), mole),
            Triple("freckles_and_mole", freckles, mole),
        )) {
            put("complexion/$name", layer { face(*spots, color = grey(235)); face(*moles, color = grey(120)) })
        }

        put("vitiligo/0", layer {
            face(0 to 6, 0 to 7, 1 to 7, 6 to 1, 7 to 1, 7 to 2)
            rect(44, 29, 4, 3, WHITE); rect(37, 61, 3, 3, WHITE) // hands
        })
        put("vitiligo/1", layer {
            face(3 to 0, 4 to 0, 5 to 0, 4 to 1, 1 to 2)
            rect(21, 20, 4, 3, WHITE); rect(46, 23, 2, 4, WHITE) // chest, arm
        })

        // Elven markings (emissive).
        put("markings/circlet", layer { rect(0, 9, 32, 1, WHITE); face(3 to 2, 4 to 2) })
        put("markings/tendrils", layer { face(1 to 5, 1 to 6, 1 to 7, 6 to 5, 6 to 6, 6 to 7, 3 to 1, 4 to 1, 3 to 2, 4 to 2) })
        put("markings/runes", layer { face(2 to 1, 4 to 1, 6 to 1, 3 to 2, 5 to 2, 0 to 5, 7 to 5) })

        // Facial hair (tinted with the hair colour).
        put("facial_hair/stubble", layer { face(0 to 6, 2 to 6, 5 to 6, 7 to 6, 1 to 7, 3 to 7, 4 to 7, 6 to 7, color = grey(215)) })
        put("facial_hair/beard", layer {
            face(0 to 5, 7 to 5, 0 to 6, 1 to 6, 2 to 6, 5 to 6, 6 to 6, 7 to 6, *Array(8) { it to 7 }, color = grey(230))
            rect(16, 0, 8, 2, grey(215)) // under the chin
        })

        // Hair: head and brows; long styles continue down the jacket's back.
        val fringe = Array(8) { it to 0 }
        put("hair/short_crop", hair(3, 5, *fringe))
        put("hair/short_swept", hair(4, 6, *fringe, 0 to 1, 1 to 1, 2 to 1, 3 to 1))
        put("hair/long_straight", hair(8, 8, *fringe, *Array(7) { 0 to it + 1 }, *Array(7) { 7 to it + 1 }) { rect(32, 36, 8, 7, grey(225)) })
        put("hair/long_braid", hair(5, 8, *fringe, 0 to 1, 7 to 1) { rect(35, 36, 2, 10, grey(210)) })

        // Clothes (not tinted).
        val green = rgb(62, 122, 74)
        val darkGreen = rgb(40, 88, 52)
        val brown = rgb(107, 74, 43)
        val leather = rgb(80, 55, 34)
        val trousers = rgb(90, 74, 58)
        put("clothes/x", layer { // long dress with a belt, short sleeves, soft boots
            fillBox(JACKET, green, sideColor = darkGreen)
            rect(16, 41, 24, 1, brown)
            for (s in SLEEVES) fillBox(s, green, rows = 0 to 6, sideColor = darkGreen)
            for (p in PANTS) {
                fillBox(p, green, rows = 0 to 7, sideColor = darkGreen)
                fillBox(p, leather, rows = 9 to 12)
            }
        })
        put("clothes/y", layer { // tunic over trousers, full sleeves with cuffs, boots
            fillBox(JACKET, green, rows = 0 to 10, sideColor = darkGreen)
            rect(16, 43, 24, 1, brown)
            for (s in SLEEVES) {
                fillBox(s, green, sideColor = darkGreen)
                fillBox(s, brown, rows = 10 to 12)
            }
            for (p in PANTS) {
                fillBox(p, trousers)
                fillBox(p, leather, rows = 8 to 12)
            }
        })
    }

    private fun hair(sideRows: Int, backRows: Int, vararg fringe: Pair<Int, Int>, extra: Canvas.() -> Unit = {}) = layer {
        rect(8, 0, 8, 8, grey(240))                   // top
        for (x0 in listOf(0, 16)) rect(x0, 8, 8, sideRows, grey(225)) // sides
        rect(24, 8, 8, backRows, grey(225))           // back of the head
        face(*fringe, color = grey(240))
        face(1 to 3, 2 to 3, 5 to 3, 6 to 3, color = grey(200)) // brows
        for (x in 8 until 32 step 3) {                // strand shading
            for (y in 8 until 8 + maxOf(sideRows, backRows)) if (isSet(x, y)) this[x, y] = grey(195)
        }
        // Ponytail strip (shared by every ponytail style): strands with darker grooves.
        rect(56, 32, 8, 14, grey(228))
        for (x in 56 until 64 step 2) rect(x, 34, 1, 12, grey(200))
        extra()
    }
}

/** The Drow share the elf model, face and hair art; only their clothes and dye accents are their own. */
object DrowTextures {
    fun all(): Map<String, Canvas> = buildMap {
        val silk = rgb(42, 34, 56)
        val silkDark = rgb(28, 22, 38)
        val trim = rgb(184, 188, 200)
        val leatherDark = rgb(34, 28, 30)
        put("clothes/x", Canvas().apply { // high-collared gown with silver trim
            fillBox(JACKET, silk, sideColor = silkDark)
            rect(16, 40, 24, 1, trim)
            rect(23, 36, 2, 12, trim) // centre seam
            for (s in SLEEVES) {
                fillBox(s, silk, sideColor = silkDark)
                fillBox(s, trim, rows = 11 to 12)
            }
            for (p in PANTS) {
                fillBox(p, silk, rows = 0 to 9, sideColor = silkDark)
                fillBox(p, leatherDark, rows = 9 to 12)
            }
        })
        put("clothes/y", Canvas().apply { // fitted coat over dark trousers
            fillBox(JACKET, silk, rows = 0 to 11, sideColor = silkDark)
            rect(16, 42, 24, 1, trim)
            rect(20, 36, 1, 10, trim); rect(27, 36, 1, 10, trim) // lapels
            for (s in SLEEVES) {
                fillBox(s, silk, sideColor = silkDark)
                fillBox(s, trim, rows = 10 to 11)
            }
            for (p in PANTS) {
                fillBox(p, silkDark)
                fillBox(p, leatherDark, rows = 8 to 12)
            }
        })

        // Dye accents: greyscale, tinted with a per-individual random dye colour. Index 0 means "none" (skipped).
        put("accents/bangs_1", Canvas().apply { for (x in listOf(1, 2, 5, 6)) this[8 + x, 8] = WHITE }) // dyed fringe tips
        put("accents/bangs_2", Canvas().apply { // side-swept streak
            for ((x, y) in listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1)) this[8 + x, 8 + y] = WHITE
            rect(0, 8, 3, 3, WHITE)
        })
        put("accents/ponytail_1", Canvas().apply { rect(56, 41, 8, 5, WHITE) }) // dyed ponytail ends
        put("accents/ponytail_2", Canvas().apply {
            // A coloured extension braided in: one strand spiralling down the tail, stepping one face sideways
            // every few rows so it reads as woven through rather than as rings.
            val strip = PlayerSheet.PONYTAIL
            val sides = listOf(Face.EAST, Face.NORTH, Face.WEST, Face.SOUTH)
            for (row in 0 until strip.h) {
                val face = sides[(row / 3) % sides.size]
                val r = strip.rect(face)
                this[r.x + (row % 2), r.y + row] = WHITE
                if (row % 3 != 2) this[r.x + 1 - (row % 2), r.y + row] = WHITE
            }
        })
    }
}
