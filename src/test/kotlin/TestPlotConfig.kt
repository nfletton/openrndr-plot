import org.openrndr.color.ColorRGBa
import org.openrndr.shape.Rectangle
import kotlin.test.*

class TestPlotConfig {
    @Test
    fun `plot config with pen tool type does not require a well for each color`() {
        val plotConfig =
            PlotConfig(
                toolType = DrawTool.Pen,
                palette = mapOf(ColorRGBa.RED to "red", ColorRGBa.GREEN to "green"))
        assertTrue(plotConfig.eachColorHasAWell())
    }

    @Test
    fun `plot config with dip tool type requires a well for each color`() {
        // color for all wells
        val plotConfig1 = PlotConfig(
            toolType = DrawTool.Dip,
            palette = mapOf(ColorRGBa.RED to "red", ColorRGBa.BLUE to "blue", ColorRGBa.GREEN to "green"),
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
                toolType = DrawTool.Dip,
                palette = mapOf(ColorRGBa.RED to "red", ColorRGBa.BLUE to "blue", ColorRGBa.GREEN to "green"),
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
            paintWells = mapOf(ColorRGBa.BLACK to listOf(Rectangle(10.0, 10.0, 10.0, 10.0))),
            washWells = listOf(Rectangle(10.0, 10.0, 10.0, 10.0)),
            toolType = DrawTool.DipAndStir
        )
        assertTrue(plotConfig1.requiresWash)

        val plotConfig2 = PlotConfig(
            paintWells = mapOf(ColorRGBa.BLACK to listOf(Rectangle(10.0, 10.0, 10.0, 10.0))),
            washWells = emptyList(),
            toolType = DrawTool.DipAndStir)
        assertFalse(plotConfig2.requiresWash)
    }
}
