package demos

import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.color.presets.BLUE_STEEL
import org.openrndr.extra.color.presets.CHOCOLATE
import org.openrndr.extra.color.presets.DARK_GREEN
import org.openrndr.extra.color.presets.INDIGO
import org.openrndr.extra.composition.composition
import org.openrndr.extra.composition.drawComposition
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector2
import org.openrndr.namedTimestamp
import org.openrndr.shape.Circle
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Triangle
import plot.DrawTool
import plot.PaperSize
import plot.PlotConfig
import plot.saveFileSet
    
/**
 * id: 597a4d58-06b3-4569-87b7-c229093b5a67
 * description: New sketch
 * tags: #new
 */    

private val screenScale: Double
    get() = 3.0

private fun Double.fromMillimetres(): Double {
    return this * screenScale
}

fun main() = application {
    configure {
        width = (PaperSize.ART_11x14.height * screenScale).toInt()
        height = (PaperSize.ART_11x14.width * screenScale).toInt()
        position = IntVector2(-width - 15, 50)
        title = "AxiDraw Demo 3 - Pain and Wash Wells"
    }
    program {
        val colorPalette = mapOf(
            ColorRGBa.BLUE_STEEL to "blue",
            ColorRGBa.CHOCOLATE to "chocolate",
            ColorRGBa.INDIGO to "indigo",
            ColorRGBa.MAGENTA to "magenta",
            ColorRGBa.DARK_GREEN to "green",
        )

        val paintWells = mapOf(
            ColorRGBa.BLUE_STEEL  to listOf(Rectangle(20.0, 40.0, 30.0, 20.0)),
            ColorRGBa.CHOCOLATE  to listOf(Rectangle(20.0, 60.0, 30.0, 20.0)),
            ColorRGBa.INDIGO  to listOf(Rectangle(20.0, 80.0, 30.0, 20.0)),
            ColorRGBa.MAGENTA  to listOf(Rectangle(20.0, 100.0, 30.0, 20.0)),
            ColorRGBa.DARK_GREEN  to listOf(
                Rectangle(20.0, 120.0, 30.0, 20.0),
                Rectangle(20.0, 140.0, 30.0, 20.0)
            ),
        )

        val washWells: List<Rectangle> = listOf(
            Rectangle(0.0, 0.0, 50.0, 40.0),
        )

        val plotConfig = PlotConfig(
            toolType = DrawTool.DipAndStir,
            displayScale = screenScale,
            paperSize = PaperSize.ART_11x14,
            palette = colorPalette,
            refillDistance = 200.0,
            paintWells = paintWells,
            washWells = washWells,
            paperOffset = Vector2(50.0, 0.0)
        )

        val border = Vector2(20.0.fromMillimetres(), 20.0.fromMillimetres())

        val drawArea = Rectangle(border.x, border.y, width - 2 * border.x, height - 2 * border.y)

        // map unit interval measurements within draw area to absolute pixel
        // in the sketch
        val x = { v: Double -> drawArea.width * v.coerceIn(0.0, 1.0) + border.x }
        val y = { v: Double -> drawArea.height * v.coerceIn(0.0, 1.0) + border.y }
        val p = { v: Vector2 -> Vector2(x(v.x), y(v.y)) }


        val composition = drawComposition {
            fill = ColorRGBa.WHITE
            stroke = ColorRGBa.DARK_GREEN
            strokeWeight = 1.0
            // default layer
            rectangle(drawArea)
            fill = null
            strokeWeight = 0.6.fromMillimetres()

            group {
                stroke = ColorRGBa.CHOCOLATE
                circle(Circle(p(Vector2(0.5, 0.5)), 25.0.fromMillimetres()))
                shape(Triangle(p(Vector2(0.2, 0.2)), p(Vector2(0.8, 0.2)), p(Vector2(0.5, 0.8))).shape)
            }.attributes["data-layer"] = "layer1"
            group {
                stroke = ColorRGBa.BLUE_STEEL
                lineSegment(p(Vector2(0.5, 0.5)), p(Vector2(0.2, 0.2)))
                lineSegment(p(Vector2(0.5, 0.5)), p(Vector2(0.8, 0.2)))
                lineSegment(p(Vector2(0.5, 0.5)), p(Vector2(0.5, 0.8)))
            }.attributes["data-layer"] = "layer2"
        }

        extend {
            drawer.composition(composition)
        }
        keyboard.keyDown.listen {
            when (it.key) {
                KEY_SPACEBAR -> {
                    composition.saveFileSet(
                        namedTimestamp(),
                        plotConfig
                    )
                }
            }
        }
    }
}
