package app.novapill.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesLineAndFractionalTimestamp() {
        val lines = LrcParser.parse("[00:12.34]First line\n[01:02.5]Second line")
        assertEquals(2, lines.size)
        assertEquals(LyricLine(12_340L, "First line"), lines[0])
        assertEquals(LyricLine(62_500L, "Second line"), lines[1])
    }

    @Test
    fun expandsMultipleTimestampsAndSortsThem() {
        val lines = LrcParser.parse("[00:14.00][00:03.50]Shared line\n[00:08.00]Middle")
        assertEquals(listOf(3_500L, 8_000L, 14_000L), lines.map { it.timestampMs })
        assertEquals("Shared line", lines[0].text)
        assertEquals("Shared line", lines[2].text)
    }

    @Test
    fun ignoresMetadataAndMalformedSeconds() {
        val lines = LrcParser.parse("[ar:Artist]\n[00:75.00]Bad\n[00:04.00]Valid")
        assertEquals(1, lines.size)
        assertEquals("Valid", lines.single().text)
    }

    @Test
    fun findsActiveLineAndAppliesOffset() {
        val lines = listOf(
            LyricLine(1_000L, "one"),
            LyricLine(2_000L, "two"),
            LyricLine(3_000L, "three")
        )
        assertEquals(-1, LrcParser.activeIndex(lines, 500L))
        assertEquals(1, LrcParser.activeIndex(lines, 2_400L))
        assertEquals(2, LrcParser.activeIndex(lines, 2_400L, offsetMs = -700L))
    }

    @Test
    fun emptyFileReturnsNoLines() {
        assertTrue(LrcParser.parse("").isEmpty())
        assertEquals(-1, LrcParser.activeIndex(emptyList(), 8_000L))
    }
}
