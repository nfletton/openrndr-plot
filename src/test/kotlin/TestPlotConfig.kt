import org.openrndr.color.ColorRGBa
import org.openrndr.shape.Rectangle
import kotlin.test.*

class TestPlotConfig {
    @Test
    fun `plot config with pen tool type does not require a well for each color`() {
        val plotConfig =
            PlotConfig(
                toolType = DrawTool.PEN,
                palette = listOf(ColorRGBa.RED, ColorRGBa.GREEN))
        assertTrue(plotConfig.eachColorHasAWell())
    }

    @Test
    fun `plot config with dip tool type requires a well for each color`() {
        // color for all wells
        val plotConfig1 = PlotConfig(
            toolType = DrawTool.DIP,
            palette = listOf(ColorRGBa.RED, ColorRGBa.BLUE, ColorRGBa.GREEN),
            paintWells = mapOf(
                ColorRGBa.RED to listOf(Rectangle(10.0, 10.0, 10.0, 10.0)),
                ColorRGBa.GREEN to listOf(Rectangle(10.0, 10.0, 10.0, 10.0)),
                ColorRGBa.BLUE to listOf(Rectangle(10.0, 10.0, 10.0, 10.0)),
            )
        )
        assertTrue(plotConfig1.eachColorHasAWell())

        // missing well for blue
        assertFailsWith<IllegalArgumentException> {
            PlotConfig(
                toolType = DrawTool.DIP,
                palette = listOf(ColorRGBa.RED, ColorRGBa.BLUE, ColorRGBa.GREEN),
                paintWells = mapOf(
                    ColorRGBa.RED to listOf(Rectangle(10.0, 10.0, 10.0, 10.0)),
                    ColorRGBa.GREEN to listOf(Rectangle(10.0, 10.0, 10.0, 10.0)),
                )
            )
        }
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
