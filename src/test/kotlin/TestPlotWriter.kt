import org.junit.Before
import org.openrndr.extra.composition.Composition
import org.openrndr.math.Vector2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TestPlotWriter {
    @Test
    fun `Vector2 list to a rounded string`() {
        val vectors = listOf(Vector2.ZERO, Vector2.ONE, Vector2(2.123456, 3.123456))
        val result = roundAndStringify(vectors)
        assertEquals("[[0.0,0.0],[1.0,1.0],[2.123,3.123]]", result)
    }
}

