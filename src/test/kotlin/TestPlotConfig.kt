import org.openrndr.color.ColorRGBa
import org.openrndr.shape.Rectangle
import kotlin.test.*

class TestPlotConfig {
    val red = ColorRGBa(1.0, 0.0, 0.0, 1.0)
    val blue = ColorRGBa(0.0, 0.0, 1.0, 1.0)
    val green = ColorRGBa(0.0, 1.0, 0.0, 1.0)

    @Test
    fun `plot config with pen tool type does not require a well for each color`() {
        val plotConfig = PlotConfig(toolType = DrawTool.PEN, palette = listOf(red, blue))
        assertTrue(plotConfig.eachColorHasAWell())
    }

    @Test
    fun `plot config with dip tool type requires a well for each color`() {
        // color for all wells
        val plotConfig1 = PlotConfig(
            toolType = DrawTool.DIP,
            palette = listOf(red, blue, green),
            paintWells = listOf(
                Pair(0, Rectangle(10.0, 10.0, 10.0, 10.0)),
                Pair(1, Rectangle(10.0, 10.0, 10.0, 10.0)),
                Pair(2, Rectangle(10.0, 10.0, 10.0, 10.0)),
            )
        )
        assertTrue(plotConfig1.eachColorHasAWell())

        // missing well for blue
        assertFailsWith<IllegalArgumentException> {
            PlotConfig(
                toolType = DrawTool.DIP,
                palette = listOf(red, blue, green),
                paintWells = listOf(
                    Pair(0, Rectangle(10.0, 10.0, 10.0, 10.0)),
                    Pair(2, Rectangle(10.0, 10.0, 10.0, 10.0)),
                )
            )
        }
    }

    @Test
    fun `getPaletteIndex should return the index of the color in the palette or -1 if not found`() {
        val plotConfig = PlotConfig(palette = listOf(blue, green, red))

        assertEquals(0, plotConfig.getPaletteIndex(blue))
        assertEquals(2, plotConfig.getPaletteIndex(red))
        assertEquals(-1, plotConfig.getPaletteIndex(ColorRGBa.YELLOW))
    }

    @Test
    fun `getPaletteIndex should return the index of the color even if alpha value does not match`() {
        val plotConfig = PlotConfig(palette = listOf(blue, green, red))

        assertEquals(2, plotConfig.getPaletteIndex(ColorRGBa(1.0, 0.0, 0.0, 0.5)))
    }

    @Test
    fun `requiresRefills should return true if refillDistance is less than positive infinity`() {
        val plotConfig1 = PlotConfig(refillDistance = Double.POSITIVE_INFINITY)
        assertFalse(plotConfig1.requiresRefills)

        val plotConfig2 = PlotConfig(refillDistance = 5.0)
        assertTrue(plotConfig2.requiresRefills)
    }

    @Test
    fun `requiresWash should be true for tool types DIP and DIP_AND_STIR if washWells is not empty`() {
        val plotConfig1 = PlotConfig(
            washWells = listOf(Rectangle(10.0, 10.0, 10.0, 10.0)),
            toolType = DrawTool.DIP_AND_STIR
        )
        assertTrue(plotConfig1.requiresWash)

        val plotConfig2 = PlotConfig(washWells = emptyList(), toolType = DrawTool.DIP_AND_STIR)
        assertFalse(plotConfig2.requiresWash)
    }
}
