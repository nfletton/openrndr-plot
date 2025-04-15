package plot

sealed class PaperSize(width: Double, height: Double) {
    val x = width
    val y = height

    // ISO
    data object A0 : PaperSize(841.0, 1189.0)
    data object A1 : PaperSize(594.0, 841.0)
    data object A2 : PaperSize(420.0, 594.0)
    data object A3 : PaperSize(297.0, 420.0)
    data object A4 : PaperSize(210.0, 297.0)
    data object A5 : PaperSize(148.0, 210.0)
    data object A6 : PaperSize(105.0, 148.0)
    data object A7 : PaperSize(74.0, 105.0)
    // Imperial
    data object QUARTER_IMPERIAL : PaperSize(280.0, 380.0)
    data object HALF_IMPERIAL : PaperSize(380.0, 560.0)
    data object IMPERIAL : PaperSize(560.0, 760.0)
    data object SMALL_SQUARE : PaperSize(150.0, 150.0)
    data object MEDIUM_SQUARE : PaperSize(200.0, 200.0)
    data object LARGE_SQUARE : PaperSize(300.0, 300.0)
    // North American letter
    data object LETTER : PaperSize(215.9, 279.4)
    data object LEGAL : PaperSize(215.9, 355.6)
    data object TABLOID : PaperSize(279.4, 431.8)
    // North American art
    data object ART_6x8 : PaperSize(152.0, 205.0)
    data object ART_9x12 : PaperSize(229.0, 305.0)
    data object ART_11x14 : PaperSize(279.0, 356.0)
    data object ART_11x15 : PaperSize(279.0, 381.0)
    data object ART_12x12 : PaperSize(305.0, 305.0)
    data object ART_12x16 : PaperSize(305.0, 406.0)
    data object ART_12x18 : PaperSize(305.0, 457.0)
    data object ART_14x17 : PaperSize(356.0, 432.0)
    data object ART_18x24 : PaperSize(457.0, 610.0)
    data object ART_22x30 : PaperSize(559.0, 762.0)

    data class Custom(private val width: Double, private val height: Double) :
        PaperSize(width, height)

    fun aspectRatio(): Double {
        return x / y
    }

    fun landscape(): PaperSize {
        return Custom(y, x)
    }
}

enum class AxiDrawTravel(val x: Double, val y: Double) {
    V3A3(430.0, 297.0),      // V3/A3 and SE/A3
    V3XLX(595.0, 218.0),      // AxiDraw V3 XLX
    SEA1(864.0, 594.0),      // AxiDraw SE/A1
    SEA2(594.0, 432.0),      // AxiDraw SE/A2
}
