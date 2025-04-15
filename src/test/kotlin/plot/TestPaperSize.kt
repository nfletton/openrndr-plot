package plot

import kotlin.test.Test
import kotlin.test.assertEquals

class TestPaperSize {

    @Test
    fun `Verify custom paper size dimensions`() {
        val customSize = PaperSize.Custom(500.0, 700.0)
        assertEquals(500.0, customSize.width)
        assertEquals(700.0, customSize.height)
    }

    @Test
    fun `verify aspect ratio of custom paper size`() {
        val customSize = PaperSize.Custom(500.0, 700.0)
        assertEquals(500.0 / 700.0, customSize.aspectRatio())
    }

    @Test
    fun `verify landscape function swaps width and height`() {
        // Test with a standard paper size
        val a7 = PaperSize.A7
        val reversedA7 = a7.landscape()
        assertEquals(a7.height, reversedA7.width)
        assertEquals(a7.width, reversedA7.height)

        // Test with a custom paper size
        val customSize = PaperSize.Custom(500.0, 700.0)
        val reversedCustom = customSize.landscape()
        assertEquals(customSize.height, reversedCustom.width)
        assertEquals(customSize.width, reversedCustom.height)
    }
}
