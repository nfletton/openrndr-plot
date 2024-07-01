package plot

import org.openrndr.extra.composition.drawComposition
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import org.openrndr.shape.Segment2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TestDeDuplication {
    @Test
    fun `contains function identifies two identical line segments`() {
        val segment1 = Segment2D(Vector2.ZERO, Vector2.ONE)
        val segment2 = Segment2D(Vector2.ZERO, Vector2.ONE)
        assertTrue(segment1.contains(segment2))
    }

    @Test
    fun `contains function identifies the exact same line segment`() {
        val segment1 = Segment2D(Vector2.ZERO, Vector2.ONE)
        assertFalse(segment1.contains(segment1))
    }

    @Test
    fun `contains function identifies a line segment as part of another segment`() {
        val segment1 = Segment2D(Vector2.ONE, Vector2(10.0, 10.0))
        val segment2 = Segment2D(Vector2(7.0, 7.0), Vector2(8.0, 8.0))
        assertTrue(segment1.contains(segment2))
    }

    @Test
    fun `contains function identifies two close line segments as not contained`() {
        val segment1 = Segment2D(Vector2.ONE, Vector2(10.0, 10.0))
        val segment2 = Segment2D(Vector2(7.1, 7.0), Vector2(8.0, 8.0))
        assertFalse(segment1.contains(segment2, 0.05))
    }

    @Test
    fun `contains function identifies a curved segment contains another segment`() {
        val segment1 = Circle(10.0, 10.0, 5.0).contour.segments[1]
        val segment2 = segment1.sub(0.3, 0.35)
        assertTrue(segment1.contains(segment2))
    }

    @Test
    fun `remove duplicate segments - identical rectangles`() {
        val comp = drawComposition {
            rectangle(100.0, 100.10, 25.0, 43.2)
            rectangle(100.0, 100.10, 25.0, 43.2)
            rectangle(100.0, 100.10, 25.0, 43.2)
            rectangle(100.0, 100.10, 25.0, 43.2)
            rectangle(100.0, 100.10, 25.0, 43.2)
            rectangle(100.0, 100.10, 25.0, 43.2)
        }.findShapes().map { shapeNode ->
            shapeNode.shape.contours.flatMap { it.segments }
        }.flatten()
        val deduplicated = deDuplicate(comp, 1.0)
        assertEquals(4, deduplicated.size)
    }

    @Test
    fun `remove duplicate segments - rectangle and overlapping line segments`() {
        val comp = drawComposition {
            lineSegment(110.0, 100.0, 121.1, 100.0)
            rectangle(100.0, 100.0, 25.0, 43.2)
            lineSegment(105.0, 100.0, 121.1, 100.0)
            lineSegment(100.0, 125.9, 100.0, 140.0)
            lineSegment(100.0, 10.0, 100.0, 210.0)
        }.findShapes().map { shapeNode ->
            shapeNode.shape.contours.flatMap { it.segments }
        }.flatten()
        val deduplicated = deDuplicate(comp, 1.0)
        assertEquals(4, deduplicated.size)
    }

}

