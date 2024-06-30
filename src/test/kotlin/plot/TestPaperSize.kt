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
}
