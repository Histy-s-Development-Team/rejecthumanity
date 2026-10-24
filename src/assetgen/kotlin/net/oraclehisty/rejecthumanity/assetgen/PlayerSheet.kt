package net.oraclehisty.rejecthumanity.assetgen

/**
 * The vanilla 64×64 player skin layout, which every elf-like model samples (so player-style art and the placeholder
 * layers line up), plus the extra parts tucked into the corners the player layout leaves empty.
 */
object PlayerSheet {
    val HEAD = Region(0, 0, 8, 8, 8)
    val HAT = Region(32, 0, 8, 8, 8)
    val BODY = Region(16, 16, 8, 12, 4)
    val JACKET = Region(16, 32, 8, 12, 4)
    val RIGHT_ARM = Region(40, 16, 4, 12, 4)
    val RIGHT_SLEEVE = Region(40, 32, 4, 12, 4)
    val LEFT_ARM = Region(32, 48, 4, 12, 4)
    val LEFT_SLEEVE = Region(48, 48, 4, 12, 4)
    val RIGHT_LEG = Region(0, 16, 4, 12, 4)
    val RIGHT_PANTS = Region(0, 32, 4, 12, 4)
    val LEFT_LEG = Region(16, 48, 4, 12, 4)
    val LEFT_PANTS = Region(0, 48, 4, 12, 4)

    // Long pointed ears: base, middle and tip, per side.
    val EAR_RIGHT_BASE = Region(0, 0, 2, 3, 1)
    val EAR_RIGHT_MID = Region(0, 4, 2, 2, 1)
    val EAR_RIGHT_TIP = Region(56, 16, 1, 1, 1)
    val EAR_LEFT_BASE = Region(24, 0, 2, 3, 1)
    val EAR_LEFT_MID = Region(24, 4, 2, 2, 1)
    val EAR_LEFT_TIP = Region(60, 16, 1, 1, 1)

    // Short broad ears.
    val EAR_RIGHT_BLOCKY = Region(56, 18, 2, 3, 2)
    val EAR_LEFT_BLOCKY = Region(56, 23, 2, 3, 2)

    /** Every ponytail cube samples this one strip, so each hair texture colours all ponytail styles. */
    val PONYTAIL = Region(56, 32, 2, 12, 2)

    val SKIN_PARTS = listOf(HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG)
    val EAR_PARTS = listOf(EAR_RIGHT_BASE, EAR_RIGHT_MID, EAR_LEFT_BASE, EAR_LEFT_MID, EAR_RIGHT_TIP, EAR_LEFT_TIP, EAR_RIGHT_BLOCKY, EAR_LEFT_BLOCKY)
    val SLEEVES = listOf(RIGHT_SLEEVE, LEFT_SLEEVE)
    val PANTS = listOf(RIGHT_PANTS, LEFT_PANTS)
}
