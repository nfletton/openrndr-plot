import org.openrndr.math.Vector2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TestPath {
    val path1 = Path(
        listOf(
            Vector2(0.0, 0.0),
            Vector2(100.0, 0.0),
            Vector2(100.0, 100.0),
            Vector2(0.0, 100.0),
            Vector2(0.0, 0.0)
        )
    )
    val path2 = Path(
        listOf(
            Vector2(0.0, 0.0),
            Vector2(100.0, 0.0),
            Vector2(100.0, 100.0),
            Vector2(0.0, 100.0)
        )
    )
    val path3 = Path(listOf(Vector2(100.0, 100.0), Vector2(300.0, 100.0)))
    val path4 = Path(listOf(Vector2(200.0, 200.0), Vector2(400.0, 300.0)))
    val path5 = Path(
        listOf(
            Vector2(0.0, 0.0),
            Vector2(100.0, 0.0),
            Vector2(100.0, 100.0),
            Vector2(75.0, 200.0),
            Vector2(25.0, 300.0),
            Vector2(0.0, 100.0),
            Vector2(0.0, 0.0)
        )
    )

    val tolerance = 0.0000001

    @Test
    fun testLength() {
        assertEquals(400.0, path1.length())
        assertEquals(300.0, path2.length())
    }

    @Test
    fun testClosed() {
        assertTrue(path1.closed())
        assertFalse(path2.closed())
        assertFalse(path3.closed())
    }

    @Test
    fun testDistanceToOtherStart() {
        assertEquals(141.421356237, path1.distanceToOtherStart(path3), tolerance)
    }

    @Test
    fun testDistanceToOtherEnd() {
        assertEquals(223.60679775, path3.distanceToOtherEnd(path4), tolerance)
    }

    @Test
    fun testClosestEnd() {
        assertEquals(100.0, path2.closestEnd(path3), tolerance)
        assertEquals(141.421356237, path3.closestEnd(path4), tolerance)
    }

    @Test
    fun `shiftStart should rotate start point`() {
        val pathLength = 10
        val testPoints =
            List(pathLength - 1) { Vector2(it.toDouble(), it.toDouble()) } + Vector2.ZERO
        var testPath = Path(testPoints)
        var shiftOffset = 5
        testPath.shiftStart(shiftOffset)
        assertEquals(pathLength, testPath.points.size, "New path should be same size")
        assertTrue(testPath.closed(), "New path should be closed")
        assertEquals(testPoints[shiftOffset], testPath.points[0])
        assertEquals(testPoints[shiftOffset - 1], testPath.points.get(pathLength - 2))
        shiftOffset = 12
        testPath = Path(testPoints)
        testPath.shiftStart(shiftOffset)
        assertEquals(pathLength, testPath.points.size, "New path should be same size")
        assertTrue(testPath.closed(), "New path should be closed")
        assertEquals(testPoints[3], testPath.points[0])
    }
}

