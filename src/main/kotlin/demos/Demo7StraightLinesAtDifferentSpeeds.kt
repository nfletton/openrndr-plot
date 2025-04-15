package demos

import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.composition.composition
import org.openrndr.extra.composition.drawComposition
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector2
import org.openrndr.namedTimestamp
import org.openrndr.shape.Rectangle
import plot.DrawTool
import plot.PaperSize
import plot.PlotConfig
import plot.saveFileSet

/**
 * id: 6f7ca73b-f126-431b-958c-f4dbb7dc6977
 * description: New sketch
 * tags: #new
 */

private val screenScale: Double
    get() = 3.2

private fun Double.fromMillimetres(): Double {
    return this * screenScale
}

private val paper = PaperSize.A7.landscape()

private val defaultOptions = mutableMapOf(
    "model" to 2,
    "penlift" to 3,
    "pen_pos_up" to 67,
    "pen_pos_down" to 20,
    "accel" to 50,
    "speed_pendown" to 10,
    "speed_penup" to 35,
)

fun main() {
    application {
        configure {
            width = (paper.x * screenScale).toInt()
            height = (paper.y * screenScale).toInt()
            position = IntVector2(-width - 15, 50)
            title = "AxiDraw Demo 7 - Lines at different speeds"
        }
        program {
            val plotConfig = PlotConfig(
                toolType = DrawTool.Pen,
                displayScale = screenScale,
                paperSize = paper,
//            refillDistance = 100.0,
                paperOffset = Vector2(0.0, 0.0),
                defaultOptions = defaultOptions,
            )

            val border = Vector2(10.0.fromMillimetres(), 10.0.fromMillimetres())

            val drawArea = Rectangle(border.x, border.y, width - 2 * border.x, height - 2 * border.y)

            // map unit interval measurements within draw area to absolute pixel
            // in the sketch
            val x = { v: Double -> drawArea.width * v.coerceIn(0.0, 1.0) + border.x }
            val y = { v: Double -> drawArea.height * v.coerceIn(0.0, 1.0) + border.y }
            val p = { v: Vector2 -> Vector2(x(v.x), y(v.y)) }

            val composition = drawComposition {
                backgroundColor = ColorRGBa.WHITE
                stroke = ColorRGBa.BLACK
                fill = null
                strokeWeight = 0.2.fromMillimetres()
                val lineCount = 150
                for (i in 0..lineCount) {
                    val yPos = border.y + i * (drawArea.height / lineCount)
                    lineSegment(Vector2(border.x, yPos), Vector2(border.x + drawArea.width, yPos))
                }
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
}
