package demos

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.gui.WindowedGUI
import org.openrndr.extra.olive.Reloadable
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.transform
import org.openrndr.shape.Segment2D
import org.openrndr.shape.ShapeContour


fun main() = application {
    configure {
        width = 800
        height = 800
    }
    oliveProgram {
        val segments = listOf(
            Segment2D(
                start = Vector2(x = 228.92695358245783 * 3.2, y = 89.15260390145781 * 3.2),
                end = Vector2(x = 176.7291808175439 * 3.2, y = 156.69916470825373 * 3.2),
                control = listOf(
                    Vector2(
                        x = 279.33010513827196 * 3.2,
                        y = 162.79033741783965 * 3.2
                    ), Vector2(x = 174.86439601921532 * 3.2, y = 213.86627398539545 * 3.2)
                )
            ),
            Segment2D(
                start = Vector2(x = 176.7291808175439 * 3.2, y = 156.69916470825373 * 3.2),
                end = Vector2(x = 179.65259807198356 * 3.2, y = 102.44743220944142 * 3.2),
                control = listOf(
                    Vector2(x = 177.5571370306797 * 3.2, y = 131.31722071372616 * 3.2),
                    Vector2(x = 209.71890325272318 * 3.2, y = 75.26010188407557 * 3.2)
                )
            ),
            Segment2D(
                start = Vector2(x = 179.65259807198356 * 3.2, y = 102.44743220944142 * 3.2),
                end = Vector2(x = 165.60339654118928 * 3.2, y = 125.33770746423443 * 3.2),
                control = listOf(
                    Vector2(
                        x = 173.34913900137542 * 3.2,
                        y = 108.14730862973494 * 3.2
                    ), Vector2(x = 179.0689095160551 * 3.2, y = 137.95617885209026 * 3.2)
                )
            ),
            Segment2D(
                start = Vector2(x = 165.60339654118928 * 3.2, y = 125.33770746423443 * 3.2),
                end = Vector2(x = 161.54138304639477 * 3.2, y = 109.04326746258711 * 3.2),
                control = listOf(
                    Vector2(x = 161.712563789129 * 3.2, y = 121.69162566635224 * 3.2),
                    Vector2(x = 170.5123389832042 * 3.2, y = 109.74266038029543 * 3.2)
                )
            ),
            Segment2D(
                start = Vector2(x = 161.54138304639477 * 3.2, y = 109.04326746258711 * 3.2),
                end = Vector2(x = 156.93372946774954 * 3.2, y = 117.0539951333075 * 3.2),
                control = listOf(
                    Vector2(
                        x = 158.14683026902333 * 3.2,
                        y = 108.77862162238861 * 3.2
                    ), Vector2(x = 156.76433944482594 * 3.2, y = 112.95884800045775 * 3.2)
                )
            ),
        )
        val contour = ShapeContour.fromSegments(segments, false)
        var points2 = listOf<List<Vector2>>()
        val windowedGUI = WindowedGUI()

        val settings = object : Reloadable() {
            @DoubleParameter("Tolerance", 0.001, 20.0, order = 1)
            var tolerance = 5.0
        }
        extend(windowedGUI) {
            gui.compartmentsCollapsedByDefault = false
            gui.onChange { _, _ ->
                points2 =
                    contour.segments.map { segment -> segment.adaptivePositions(settings.tolerance) }
            }
            add(settings)
        }

        extend {
            drawer.model *= transform {
                translate(-600.0, -300.0)
                scale(1.5)
            }
            drawer.clear(ColorRGBa.PINK)
            drawer.stroke = ColorRGBa.BLACK
            drawer.contour(contour)
            drawer.stroke = ColorRGBa.RED
            drawer.fill = ColorRGBa.RED
            drawer.stroke = ColorRGBa.YELLOW
            drawer.fill = ColorRGBa.YELLOW

            points2.forEach {
                drawer.stroke = ColorRGBa.RED
                drawer.fill = ColorRGBa.RED
                drawer.strokeWeight = 0.75
                drawer.circles(it, 2.0)
            }
        }
    }
}
