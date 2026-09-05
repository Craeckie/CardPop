package com.cardpop.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportFormatDetectorTest {

    private fun detect(s: String) = ImportFormatDetector.detect(s.toByteArray(Charsets.UTF_8))

    @Test
    fun `zip magic number is detected as anki deck`() {
        val header = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)
        assertEquals(ImportFormat.ANKI, ImportFormatDetector.detect(header))
    }

    @Test
    fun `pleco export is detected regardless of file name`() {
        assertEquals(
            ImportFormat.PLECO_XML,
            detect("""<?xml version="1.0" encoding="UTF-8"?><plecoflash formatversion="2"><cards/></plecoflash>""")
        )
    }

    @Test
    fun `pleco export without xml declaration is detected`() {
        assertEquals(ImportFormat.PLECO_XML, detect("""<plecoflash formatversion="2"><cards/></plecoflash>"""))
    }

    @Test
    fun `pleco export with byte order mark is detected`() {
        assertEquals(ImportFormat.PLECO_XML, detect("\uFEFF<?xml version=\"1.0\"?><plecoflash/>"))
    }

    @Test
    fun `pleco export with leading whitespace is detected`() {
        assertEquals(ImportFormat.PLECO_XML, detect("\n  <?xml version=\"1.0\"?><plecoflash/>"))
    }

    @Test
    fun `other xml is unsupported rather than treated as csv`() {
        assertEquals(
            ImportFormat.UNSUPPORTED_XML,
            detect("""<?xml version="1.0"?><rss version="2.0"><channel/></rss>""")
        )
    }

    @Test
    fun `plain csv is detected as csv`() {
        assertEquals(ImportFormat.CSV, detect("question,answer\n链接,link\n"))
    }

    @Test
    fun `tab separated text is detected as csv`() {
        assertEquals(ImportFormat.CSV, detect("question\tanswer\n链接\tlink\n"))
    }

    @Test
    fun `empty file is treated as csv`() {
        assertEquals(ImportFormat.CSV, ImportFormatDetector.detect(ByteArray(0)))
    }

    @Test
    fun `binary content with nul bytes is unsupported`() {
        val header = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x00, 0x05)
        assertEquals(ImportFormat.UNSUPPORTED_BINARY, ImportFormatDetector.detect(header))
    }
}
