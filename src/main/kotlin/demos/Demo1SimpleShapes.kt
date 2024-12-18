package demos

import plot.PaperSize
import plot.PlotConfig
import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.composition.composition
import org.openrndr.extra.composition.drawComposition
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector2
import org.openrndr.namedTimestamp
import org.openrndr.shape.Circle
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Triangle
import plot.saveAxiDrawFileSet
    
/**
 * id: 97a04125-4a63-4fd6-972b-903a3186f15d
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
        width = (PaperSize.A5.height * screenScale).toInt()
        height = (PaperSize.A5.width * screenScale).toInt()
        position = IntVector2(-width - 15, 50)
        title = "AxiDraw Demo 1 - Simple Shapes"
    }
    program {
        val plotConfig = PlotConfig(
            displayScale = screenScale,
            paperSize = PaperSize.A5,
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
            stroke = ColorRGBa.BLACK
            strokeWeight = 1.0
            rectangle(drawArea)
            fill = null
            strokeWeight = 0.6.fromMillimetres()
            circle(Circle(p(Vector2(0.5, 0.5)), 25.0.fromMillimetres()))
            shape(Triangle(p(Vector2(0.2, 0.2)), p(Vector2(0.8, 0.2)), p(Vector2(0.5, 0.8))).shape)
            lineSegment(p(Vector2(0.5, 0.5)), p(Vector2(0.2, 0.2)))
            lineSegment(p(Vector2(0.5, 0.5)), p(Vector2(0.8, 0.2)))
            lineSegment(p(Vector2(0.5, 0.5)), p(Vector2(0.5, 0.8)))
        }

        extend {
            drawer.composition(composition)
        }
        keyboard.keyDown.listen {
            when (it.key) {
                KEY_SPACEBAR -> {
                    val filename = "screenshots/${namedTimestamp()}"
                    composition.saveAxiDrawFileSet(filename, plotConfig)
                }
            }
        }
    }
}
