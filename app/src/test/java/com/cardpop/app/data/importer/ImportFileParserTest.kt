package com.cardpop.app.data.importer

import com.cardpop.app.data.anki.AnkiParser
import com.cardpop.app.data.csv.CsvParser
import com.cardpop.app.data.pleco.PlecoXmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ImportFileParserTest {

    private lateinit var parser: ImportFileParser
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        parser = ImportFileParser(AnkiParser(), PlecoXmlParser(), CsvParser())
        cacheDir = RuntimeEnvironment.getApplication().cacheDir
    }

    private fun parse(content: String) =
        parser.parse(content.byteInputStream(), cacheDir)

    @Test
    fun `pleco xml is routed to the pleco parser and fully consumed`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><plecoflash formatversion="2"><cards>""" +
            """<card language="chinese"><entry><headword charset="sc">链接</headword>""" +
            """<pron type="hypy" tones="numbers">lian4jie1</pron>""" +
            """<defn>link (on a website)</defn></entry></card>""" +
            """</cards></plecoflash>"""

        val outcome = parse(xml)

        assertTrue(outcome is ImportParseOutcome.Parsed)
        outcome as ImportParseOutcome.Parsed
        assertEquals(ImportFormat.PLECO_XML, outcome.format)
        assertEquals(1, outcome.result.validCards.size)
        assertEquals("链接", outcome.result.validCards[0].question)
    }

    @Test
    fun `pleco xml longer than the sniff window is still fully parsed`() {
        val cards = (1..500).joinToString("") {
            """<card language="chinese"><entry><headword charset="sc">链接</headword>""" +
                """<pron type="hypy" tones="numbers">lian4jie1</pron>""" +
                """<defn>link number $it</defn></entry></card>"""
        }
        val xml = """<?xml version="1.0" encoding="UTF-8"?><plecoflash formatversion="2"><cards>$cards</cards></plecoflash>"""

        val outcome = parse(xml) as ImportParseOutcome.Parsed

        assertEquals(ImportFormat.PLECO_XML, outcome.format)
        assertEquals(500, outcome.result.validCards.size)
    }

    @Test
    fun `csv is routed to the csv parser`() {
        val outcome = parse("question,answer\n链接,link\n错误,mistake\n") as ImportParseOutcome.Parsed

        assertEquals(ImportFormat.CSV, outcome.format)
        assertEquals(2, outcome.result.validCards.size)
    }

    @Test
    fun `apkg is routed to the anki parser`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK.apkg")!!

        val outcome = parser.parse(stream, cacheDir) as ImportParseOutcome.Parsed

        assertEquals(ImportFormat.ANKI, outcome.format)
        assertEquals(58, outcome.result.validCards.size)
    }

    @Test
    fun `non-pleco xml is reported unsupported instead of parsed as csv`() {
        val outcome = parse("""<?xml version="1.0"?><rss version="2.0"><channel><item>a,b</item></channel></rss>""")

        assertEquals(ImportParseOutcome.Unsupported(ImportFormat.UNSUPPORTED_XML), outcome)
    }

    @Test
    fun `binary content is reported unsupported`() {
        val outcome = parser.parse(byteArrayOf(0x00, 0x01, 0x02, 0x00).inputStream(), cacheDir)

        assertEquals(ImportParseOutcome.Unsupported(ImportFormat.UNSUPPORTED_BINARY), outcome)
    }
}
