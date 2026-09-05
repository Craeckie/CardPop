package com.cardpop.app.data.pleco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlecoXmlParserTest {

    private lateinit var parser: PlecoXmlParser

    @Before
    fun setUp() {
        parser = PlecoXmlParser()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parse(xml: String) = parser.parse(
        """<?xml version="1.0" encoding="UTF-8"?><plecoflash formatversion="2">
            <cards>$xml</cards>
        </plecoflash>""".trimIndent().byteInputStream()
    )

    private fun card(
        sc: String? = null,
        tc: String? = null,
        pron: String? = null,
        defn: String? = null,
        category: String? = null
    ): String {
        val hwSc = if (sc != null) """<headword charset="sc">$sc</headword>""" else ""
        val hwTc = if (tc != null) """<headword charset="tc">$tc</headword>""" else ""
        val pronEl = if (pron != null) """<pron type="hypy" tones="numbers">$pron</pron>""" else ""
        val defnEl = if (defn != null) """<defn>$defn</defn>""" else ""
        val catEl = if (category != null) """<catassign category="$category"/>""" else ""
        return """<card language="chinese"><entry>$hwSc$hwTc$pronEl$defnEl</entry>$catEl</card>"""
    }

    // ── Basic card parsing ────────────────────────────────────────────────────

    @Test
    fun `simplified word with pinyin and short definition`() {
        val result = parse(card(sc = "链接", tc = "鏈接", pron = "lian4jie1", defn = "link (on a website)"))
        assertEquals(1, result.validCards.size)
        assertEquals(0, result.errors.size)
        val c = result.validCards[0]
        assertEquals("链接", c.question)
        assertEquals("liànjiē\nlink (on a website)", c.answer)
    }

    @Test
    fun `long definition is trimmed at first CJK character`() {
        val result = parse(card(
            sc = "错误", tc = "錯誤", pron = "cuo4wu4",
            defn = "noun mistake; error; blunder 错误百出 cuòwù bǎi chū riddled with errors"
        ))
        val c = result.validCards[0]
        assertEquals("cuòwù", c.answer.lines()[0])
        assertEquals("noun mistake; error; blunder", c.answer.lines()[1])
    }

    @Test
    fun `single-line definition without CJK is kept whole`() {
        val result = parse(card(sc = "链接", pron = "lian4jie1", defn = "link (on a website)"))
        assertEquals("link (on a website)", result.validCards[0].answer.lines().last())
    }

    @Test
    fun `German style definition without CJK is kept whole`() {
        val result = parse(card(sc = "显示", pron = "xian3shi4", defn = "• Darstellung, Anzeige, Display (S)• einblenden"))
        assertTrue(result.validCards[0].answer.contains("• Darstellung"))
    }

    @Test
    fun `numbered definition without CJK is kept whole`() {
        val result = parse(card(sc = "版本", pron = "ban3ben3", defn = "1 version 2 edition 3 release"))
        assertEquals("1 version 2 edition 3 release", result.validCards[0].answer.lines().last())
    }

    // ── Headword fallback ─────────────────────────────────────────────────────

    @Test
    fun `falls back to traditional when no simplified`() {
        val result = parse(card(tc = "鏈接", pron = "lian4jie1", defn = "link"))
        assertEquals("鏈接", result.validCards[0].question)
    }

    @Test
    fun `simplified preferred over traditional`() {
        val result = parse(card(sc = "链接", tc = "鏈接", pron = "lian4jie1", defn = "link"))
        assertEquals("链接", result.validCards[0].question)
    }

    // ── Missing fields ────────────────────────────────────────────────────────

    @Test
    fun `card without pron gives definition-only answer`() {
        val result = parse(card(sc = "链接", defn = "link (on a website)"))
        assertEquals(1, result.validCards.size)
        assertEquals("link (on a website)", result.validCards[0].answer)
    }

    @Test
    fun `card without defn gives pinyin-only answer`() {
        val result = parse(card(sc = "链接", pron = "lian4jie1"))
        assertEquals(1, result.validCards.size)
        assertEquals("liànjiē", result.validCards[0].answer)
    }

    @Test
    fun `card with no headword goes to errors`() {
        val result = parse(card(pron = "lian4jie1", defn = "link"))
        assertEquals(0, result.validCards.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `card with no headword does not throw`() {
        // Should complete normally even with a malformed card
        val result = parse(
            card(sc = "链接", pron = "lian4jie1", defn = "link") +
            card(pron = "bad1", defn = "no headword") +
            card(sc = "错误", pron = "cuo4wu4", defn = "mistake")
        )
        assertEquals(2, result.validCards.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `card with neither pron nor defn goes to errors`() {
        val result = parse(card(sc = "链接"))
        assertEquals(0, result.validCards.size)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].reason.contains("pronunciation or definition"))
    }

    // ── Category is always null (user picks at import time) ───────────────────

    @Test
    fun `category field is always null regardless of catassign`() {
        val result = parse(card(sc = "版本", pron = "ban3ben3", defn = "version", category = "Technik"))
        assertEquals(null, result.validCards[0].category)
    }

    // ── Multi-card batch ──────────────────────────────────────────────────────

    @Test
    fun `multiple valid cards all parsed`() {
        val xml = card(sc = "链接", pron = "lian4jie1", defn = "link") +
            card(sc = "错误", pron = "cuo4wu4", defn = "mistake") +
            card(sc = "键盘", pron = "jian4pan2", defn = "keyboard")
        val result = parse(xml)
        assertEquals(3, result.validCards.size)
        assertEquals(0, result.errors.size)
    }

    // ── Separable pron marker ─────────────────────────────────────────────────

    @Test
    fun `separable marker in pron is stripped`() {
        val result = parse(card(sc = "发炎", pron = "fa1//yan2", defn = "inflame"))
        assertEquals("fāyán", result.validCards[0].answer.lines()[0])
    }

    // ── Multi-sense definitions ───────────────────────────────────────────────

    @Test
    fun `second sense after an example sentence is kept`() {
        val result = parse(card(
            sc = "允许", tc = "允許", pron = "yun3xu3",
            defn = "noun permission 得到家长的允许 dédào jiāzhǎng deyǔnxǔ gain permission " +
                "from one’s parents verb permit; allow 不允许破坏纪律 bù yǔnxǔ pòhuài jìlǜ " +
                "permit no breach of discipline"
        ))
        assertEquals(
            listOf("yǔnxǔ", "noun permission", "verb permit; allow"),
            result.validCards[0].answer.lines()
        )
    }

    @Test
    fun `sense appearing after several examples is kept`() {
        val result = parse(card(
            sc = "辛苦", pron = "xin1ku3",
            defn = "verb work hard 路上辛苦了。 Lùshang xīnkǔ le. You must have had a tiring " +
                "journey. 同志们辛苦了。 Tóngzhì men xīnkǔ le. You comrades have been working " +
                "hard. adjective hard; strenuous 犁地这活儿很辛苦。 Lí dì zhè huór hěn xīnkǔ. " +
                "Ploughing is hard work."
        ))
        assertEquals(
            listOf("xīnkǔ", "verb work hard", "adjective hard; strenuous"),
            result.validCards[0].answer.lines()
        )
    }

    @Test
    fun `example translations without a part of speech marker are dropped`() {
        val result = parse(card(
            sc = "崇拜", pron = "chong2bai4",
            defn = "verb worship; adore 偶像崇拜 Ǒuxiàng chóngbài worship of idols; idolatry " +
                "她很崇拜她父亲。 Tā hěn chóngbài tā fùqin. She worships her father."
        ))
        assertEquals(
            listOf("chóngbài", "verb worship; adore"),
            result.validCards[0].answer.lines()
        )
    }

    @Test
    fun `capitalised part of speech word in example text does not start a sense`() {
        val result = parse(card(
            sc = "名词", pron = "ming2ci2",
            defn = "noun noun (grammar) 这是名词。 Zhè shì míngcí. Noun is a word class."
        ))
        assertEquals(
            listOf("míngcí", "noun noun (grammar)"),
            result.validCards[0].answer.lines()
        )
    }

    @Test
    fun `multi-line definition keeps its line breaks`() {
        val result = parse(card(
            sc = "邮箱", pron = "you2xiang1",
            defn = "• Briefkasten (m) (S)\n• Mailbox (S) (EDV/Informatik)\n• E-Mail- Adresse (S)"
        ))
        assertEquals(
            listOf(
                "yóuxiāng",
                "• Briefkasten (m) (S)",
                "• Mailbox (S) (EDV/Informatik)",
                "• E-Mail- Adresse (S)"
            ),
            result.validCards[0].answer.lines()
        )
    }

    @Test
    fun `blank lines inside a definition are dropped`() {
        val result = parse(card(sc = "邮箱", pron = "you2xiang1", defn = "• Briefkasten\n\n• Mailbox"))
        assertEquals(
            listOf("yóuxiāng", "• Briefkasten", "• Mailbox"),
            result.validCards[0].answer.lines()
        )
    }

    @Test
    fun `definition starting with hanzi is kept whole`() {
        val result = parse(card(sc = "崇拜", pron = "chong2bai4", defn = "偶像崇拜 worship of idols"))
        assertEquals("偶像崇拜 worship of idols", result.validCards[0].answer.lines().last())
    }
}
