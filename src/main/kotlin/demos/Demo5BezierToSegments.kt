package demos

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.Segment2D
import plot.bezierCurveToPoints


fun main() = application {
    configure {
        width = 800
        height = 800
    }
    program {
        val curve = Segment2D(Vector2(50.0, 50.0), Vector2(400.0, 300.0), Vector2(100.0, 700.0), Vector2(700.0, 700.0))
        val tolerance = 0.0004
        val points = curve.bezierCurveToPoints(tolerance)

        extend {
            drawer.clear(ColorRGBa.PINK)
            drawer.stroke = ColorRGBa.BLACK
            drawer.segment(curve)
            drawer.stroke = ColorRGBa.RED
            drawer.fill = ColorRGBa.RED
            drawer.stroke = ColorRGBa.YELLOW
            drawer.fill = ColorRGBa.YELLOW
            drawer.circles(points, 2.0)
        }
    }
}
