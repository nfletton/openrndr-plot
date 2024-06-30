package plot

import org.openrndr.color.ColorRGBa
import org.openrndr.extra.composition.drawComposition
import org.openrndr.math.Vector2
import org.openrndr.shape.LineSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TestPlotWriter {
    @Test
    fun `Vector2 list to a rounded string`() {
        val vectors = listOf(Vector2.ZERO, Vector2.ONE, Vector2(2.123456, 3.123456))
        val result = roundAndStringify(vectors)
        assertEquals("[[0.0,0.0],[1.0,1.0],[2.123,3.123]]", result)
    }

    @Test
    fun `group segments by color with default layer assignment`() {
        val composition = drawComposition {
            stroke = ColorRGBa.BLUE
            rectangle(20.0, 20.0,20.0, 20.0,)
            stroke = ColorRGBa.YELLOW
            circle(50.0, 50.0, 20.0,)
        }

        val layers = groupSegmentsByLayerAndColor(composition, 1.0, Vector2.ZERO)

        assertTrue(layers.size == 1)
        assertTrue( layers.containsKey("base"))
        assertTrue( layers["base"]!!.size == 2)
        assertTrue(layers["base"]!!.containsKey(ColorRGBa.BLUE))
        assertTrue(layers["base"]?.get(ColorRGBa.BLUE)!!.size == 4)
        assertTrue(layers["base"]!!.containsKey(ColorRGBa.YELLOW))
        assertTrue(layers["base"]?.get(ColorRGBa.YELLOW)!!.size == 4)
    }

    @Test
    fun `group segments by color with specified layers`() {
        val composition = drawComposition {
            stroke = ColorRGBa.BLUE
            rectangle(20.0, 20.0,20.0, 20.0,)?.attributes?.set("layer", "layer1")
            rectangle(40.0, 40.0,20.0, 20.0,)
            stroke = ColorRGBa.YELLOW
            circle(50.0, 50.0, 20.0,)?.attributes?.set("layer", "layer2")
            circle(100.0, 100.0, 20.0,)
            circle(150.0, 150.0, 20.0,)
            lineSegment(50.0, 50.0, 150.0, 150.0)
        }

        val layers = groupSegmentsByLayerAndColor(composition, 1.0, Vector2.ZERO)

        assertTrue(layers.size == 3)
        assertTrue( layers.containsKey("base"))
        assertTrue( layers.containsKey("layer1"))
        assertTrue( layers.containsKey("layer2"))
        assertTrue( layers["base"]!!.size == 0)
        assertTrue( layers["layer1"]!!.size == 1)
        assertTrue( layers["layer2"]!!.size == 1)
        assertTrue(layers["layer1"]!!.containsKey(ColorRGBa.BLUE))
        assertTrue(layers["layer1"]?.get(ColorRGBa.BLUE)!!.size == 8)
        assertTrue(layers["layer2"]!!.containsKey(ColorRGBa.YELLOW))
        assertTrue(layers["layer2"]?.get(ColorRGBa.YELLOW)!!.size == 13)
    }

    @Test
    fun `order segments within each layer`() {
        val expected = listOf(
            LineSegment(0.0, 10.0, 10.0, 10.0).segment,
            LineSegment(20.0, 10.0, 30.0, 10.0).segment,
            LineSegment(40.0, 10.0, 50.0, 10.0).segment,
            LineSegment(60.0, 10.0, 70.0, 10.0).segment,
            LineSegment(80.0, 10.0, 100.0, 10.0).segment,
        )

        val segmentLayers: SegmentLayers = mutableMapOf(
            "base" to mutableMapOf(
                ColorRGBa.RED to expected.shuffled().toMutableList(),
                ColorRGBa.BLUE to expected.shuffled().toMutableList(),
            ),
            "layer" to mutableMapOf(
                ColorRGBa.RED to expected.shuffled().toMutableList(),
                ColorRGBa.BLUE to expected.shuffled().toMutableList(),
            ),
        )
        orderSegments(segmentLayers)

        segmentLayers.forEach { (_, layer) ->
            layer.forEach { (_, segments) ->
                assertEquals(expected.size, segments.size)
                assertEquals(expected, segments)
            }
        }
    }
}

