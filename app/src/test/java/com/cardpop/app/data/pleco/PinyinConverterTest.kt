package com.cardpop.app.data.pleco

import org.junit.Assert.assertEquals
import org.junit.Test

class PinyinConverterTest {

    private fun convert(input: String) = PinyinConverter.toToneMarks(input)

    // ── Basic syllable conversion ─────────────────────────────────────────────

    @Test fun `tone 1 places macron`() = assertEquals("jiē", convert("jie1"))
    @Test fun `tone 2 places acute`() = assertEquals("jié", convert("jie2"))
    @Test fun `tone 3 places caron`() = assertEquals("lǐ", convert("li3"))
    @Test fun `tone 4 places grave`() = assertEquals("liàn", convert("lian4"))
    @Test fun `neutral tone 5 no diacritic`() = assertEquals("shang", convert("shang5"))
    @Test fun `no digit passthrough`() = assertEquals("de", convert("de"))

    // ── Verified Pleco sample targets ────────────────────────────────────────

    @Test fun `lian4jie1 = lianjiē`() = assertEquals("liànjiē", convert("lian4jie1"))
    @Test fun `li3lun4shang5`() = assertEquals("lǐlùnshang", convert("li3lun4shang5"))
    @Test fun `fa1 separator yan2`() = assertEquals("fāyán", convert("fa1//yan2"))
    @Test fun `zong3er2yan2zhi1`() = assertEquals("zǒngéryánzhī", convert("zong3er2yan2zhi1"))
    @Test fun `multi-word qian2duan1 kuang4jia4`() =
        assertEquals("qiánduān kuàngjià", convert("qian2duan1 kuang4jia4"))
    @Test fun `Wei1ruan3 preserves capital`() = assertEquals("Wēiruǎn", convert("Wei1ruan3"))
    @Test fun `Gu3ge1 preserves capital`() = assertEquals("Gǔgē", convert("Gu3ge1"))
    @Test fun `ba2ya2`() = assertEquals("báyá", convert("ba2ya2"))
    @Test fun `shou3shu4`() = assertEquals("shǒushù", convert("shou3shu4"))

    // ── Vowel placement rules ─────────────────────────────────────────────────

    @Test fun `a beats e - xiao3`() = assertEquals("xiǎo", convert("xiao3"))
    @Test fun `ou - the o gets the mark`() = assertEquals("dōu", convert("dou1"))
    @Test fun `last vowel fallback - ji1`() = assertEquals("jī", convert("ji1"))
    @Test fun `ü normalisation - lü4`() = assertEquals("lǜ", convert("lv4"))

    // ── Blank / empty inputs ──────────────────────────────────────────────────

    @Test fun `blank string returns blank`() = assertEquals("", convert("  "))
    @Test fun `empty string returns empty`() = assertEquals("", convert(""))
}
