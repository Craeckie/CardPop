package com.cardpop.app.data.anki

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AnkiParserTest {

    private lateinit var parser: AnkiParser
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        parser = AnkiParser()
        cacheDir = RuntimeEnvironment.getApplication().cacheDir
    }

    @Test
    fun `parse HSK 3 deck returns 58 cards`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK.apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        assertEquals("Should have no parse errors", 0, result.errors.size)
        assertEquals("Should parse 58 cards", 58, result.validCards.size)
    }

    @Test
    fun `parsed cards use model name as category`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK.apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        // All cards in this deck use the "HSK" model
        assertTrue(
            "All cards should have category 'HSK'",
            result.validCards.all { it.category == "HSK" }
        )
    }

    @Test
    fun `category override replaces model name`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK.apkg")!!
        val result = parser.parse(inputStream, cacheDir, categoryOverride = "HSK 3")

        assertTrue(
            "All cards should have overridden category",
            result.validCards.all { it.category == "HSK 3" }
        )
    }

    @Test
    fun `first card uses Simplified as question and combined fields as answer`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK.apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        val firstCard = result.validCards.first()
        assertEquals("啊", firstCard.question)
        // Answer combines: Pinyin.1, Meaning, SentenceSimplified, SentencePinyin.1, SentenceMeaning
        assertTrue("Answer should contain pinyin", firstCard.answer.contains("ā, á, ǎ, à, a"))
        assertTrue("Answer should contain meaning", firstCard.answer.contains("ah; (particle showing elation"))
        assertTrue("Answer should contain example sentence", firstCard.answer.contains("这里是哪里啊？"))
        assertTrue("Answer should contain sentence pinyin", firstCard.answer.contains("Zhèli shì nǎli a?"))
        assertTrue("Answer should contain sentence meaning", firstCard.answer.contains("Where is this place?"))
    }

    @Test
    fun `cards have non-blank question and answer`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK.apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        result.validCards.forEach { card ->
            assertTrue("Question should not be blank: ${card}", card.question.isNotBlank())
            assertTrue("Answer should not be blank: ${card}", card.answer.isNotBlank())
        }
    }

    @Test
    fun `HTML tags are stripped from fields`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK.apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        result.validCards.forEach { card ->
            assertFalse(
                "Question should not contain HTML tags: ${card.question}",
                card.question.contains(Regex("<[^>]+>"))
            )
            assertFalse(
                "Answer should not contain HTML tags: ${card.answer}",
                card.answer.contains(Regex("<[^>]+>"))
            )
        }
    }

    @Test
    fun `temp directory is cleaned up after parsing`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK.apkg")!!
        parser.parse(inputStream, cacheDir)

        // No anki_import_* directories should remain
        val leftovers = cacheDir.listFiles()?.filter { it.name.startsWith("anki_import_") } ?: emptyList()
        assertTrue("Temp directories should be cleaned up", leftovers.isEmpty())
    }

    // -- Old format (.apkg with collection.anki21, models in col table JSON) --

    @Test
    fun `parse old format HSK 3 deck returns 58 cards`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK (old).apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        assertEquals("Should have no parse errors", 0, result.errors.size)
        assertEquals("Should parse 58 cards", 58, result.validCards.size)
    }

    @Test
    fun `old format parsed cards use model name as category`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK (old).apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        assertTrue(
            "All cards should have category 'HSK'",
            result.validCards.all { it.category == "HSK" }
        )
    }

    @Test
    fun `old format first card uses Simplified as question and combined fields as answer`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK (old).apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        val firstCard = result.validCards.first()
        assertEquals("啊", firstCard.question)
        assertTrue("Answer should contain pinyin", firstCard.answer.contains("ā, á, ǎ, à, a"))
        assertTrue("Answer should contain meaning", firstCard.answer.contains("ah; (particle showing elation"))
        assertTrue("Answer should contain example sentence", firstCard.answer.contains("这里是哪里啊？"))
        assertTrue("Answer should contain sentence pinyin", firstCard.answer.contains("Zhèli shì nǎli a?"))
        assertTrue("Answer should contain sentence meaning", firstCard.answer.contains("Where is this place?"))
    }

    @Test
    fun `old format cards have non-blank question and answer`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK (old).apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        result.validCards.forEach { card ->
            assertTrue("Question should not be blank: ${card}", card.question.isNotBlank())
            assertTrue("Answer should not be blank: ${card}", card.answer.isNotBlank())
        }
    }

    @Test
    fun `old format HTML tags are stripped from fields`() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("HSK_3_-_JK (old).apkg")!!
        val result = parser.parse(inputStream, cacheDir)

        result.validCards.forEach { card ->
            assertFalse(
                "Question should not contain HTML tags: ${card.question}",
                card.question.contains(Regex("<[^>]+>"))
            )
            assertFalse(
                "Answer should not contain HTML tags: ${card.answer}",
                card.answer.contains(Regex("<[^>]+>"))
            )
        }
    }

    // -- stripHtml unit tests --

    @Test
    fun `stripHtml handles br and div tags`() {
        assertEquals("line1\nline2", AnkiParser.stripHtml("line1<br>line2"))
        assertEquals("line1\nline2", AnkiParser.stripHtml("line1<br/>line2"))
        assertEquals("line1\nline2", AnkiParser.stripHtml("line1<br />line2"))
        // Leading \n from <div> gets removed by trim()
        assertEquals("content", AnkiParser.stripHtml("<div>content</div>"))
        assertEquals("line1\ncontent", AnkiParser.stripHtml("line1<div>content</div>"))
    }

    @Test
    fun `stripHtml decodes HTML entities`() {
        assertEquals("a & b", AnkiParser.stripHtml("a &amp; b"))
        assertEquals("a < b", AnkiParser.stripHtml("a &lt; b"))
        assertEquals("a > b", AnkiParser.stripHtml("a &gt; b"))
        assertEquals("a \"b\"", AnkiParser.stripHtml("a &quot;b&quot;"))
        assertEquals("中", AnkiParser.stripHtml("&#20013;"))
        assertEquals("中", AnkiParser.stripHtml("&#x4e2d;"))
    }

    @Test
    fun `stripHtml removes bold and other inline tags`() {
        assertEquals("hello world", AnkiParser.stripHtml("<b>hello</b> world"))
        assertEquals("hello", AnkiParser.stripHtml("<span style=\"color:red\">hello</span>"))
    }

    @Test
    fun `stripHtml collapses excess newlines`() {
        assertEquals("a\n\nb", AnkiParser.stripHtml("a\n\n\n\nb"))
    }
}
