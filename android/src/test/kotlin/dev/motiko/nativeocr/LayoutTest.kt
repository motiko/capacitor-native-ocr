package dev.motiko.nativeocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LayoutTest {
    @Test
    fun pixelRectBecomesNormalizedBox() {
        val box = Box.fromPixels(160.0, 100.0, 960.0, 300.0, 1600, 1000)

        assertEquals(Box(0.1, 0.1, 0.5, 0.2), box)
    }

    @Test
    fun wordRangesKeepPunctuationAndSkipRepeatedSpaces() {
        val text = "  Total:  1,234.56 EUR "
        assertEquals(listOf("Total:", "1,234.56", "EUR"), Layout.wordRanges(text).map { text.substring(it) })
        assertTrue(Layout.wordRanges("   ").isEmpty())
        assertTrue(Layout.wordRanges("").isEmpty())
    }

    @Test
    fun estimatedWordBoxSplitsLineByCharacters() {
        val text = "ab cdef"
        val ranges = Layout.wordRanges(text)
        val lineBox = Box(0.1, 0.2, 0.7, 0.05)

        val second = Layout.estimatedWordBox(ranges[1], text, lineBox)

        assertEquals(0.4, second.x, 1e-9)
        assertEquals(0.4, second.width, 1e-9)
        assertEquals(0.2, second.y, 0.0)
        assertEquals(0.05, second.height, 0.0)
    }

    @Test
    fun groupsCloseLinesAndSplitsOnGapsAndHeadings() {
        val lines = listOf(
            line("Heading", y = 0.10, height = 0.08),
            line("first", y = 0.30, height = 0.04),
            line("second", y = 0.35, height = 0.04),
            line("after gap", y = 0.60, height = 0.04),
            line("right column", y = 0.64, height = 0.04, x = 0.6),
        )

        val blocks = Layout.groupIntoBlocks(lines)

        assertEquals(listOf("Heading", "first\nsecond", "after gap", "right column"), blocks.map { it.text })
        assertTrue(blocks[1].box.isClose(Box(0.1, 0.30, 0.4, 0.09), tolerance = 1e-9))
        assertEquals("Heading\n\nfirst\nsecond\n\nafter gap\n\nright column", Layout.text(blocks))
    }

    @Test
    fun mergesReceiptPricesIntoRows() {
        // Column order: the names column, then the prices column, then the total line.
        val lines = listOf(
            line("3 x Milch", y = 0.30, height = 0.03, width = 0.3),
            line("Apfel lose", y = 0.34, height = 0.03, width = 0.3),
            line("Bananen", y = 0.38, height = 0.03, width = 0.3),
            line("3,27", y = 0.301, height = 0.03, x = 0.7, width = 0.1),
            line("2,87", y = 0.341, height = 0.03, x = 0.7, width = 0.1),
            line("1,76", y = 0.381, height = 0.03, x = 0.7, width = 0.1),
            line("Vielen Dank", y = 0.42, height = 0.03, width = 0.3),
        )

        val merged = Layout.mergeTableColumns(lines)

        assertEquals(listOf("3 x Milch 3,27", "Apfel lose 2,87", "Bananen 1,76", "Vielen Dank"), merged.map { it.text })
        assertEquals(listOf("3", "x", "Milch", "3,27"), merged[0].words.map { it.text })
        assertEquals(0.8, merged[0].box.maxX, 1e-9)
    }

    @Test
    fun keepsTwoColumnProseAsColumns() {
        val left = (0 until 4).map { line("left column line $it with several words", y = 0.3 + it * 0.04, height = 0.03, width = 0.4) }
        val right = (0 until 4).map {
            line("right column line $it with several words", y = 0.3 + it * 0.04, height = 0.03, x = 0.55, width = 0.4)
        }

        assertEquals((left + right).map { it.text }, Layout.mergeTableColumns(left + right).map { it.text })
    }

    @Test
    fun keepsSideBySideTextBlocksAsColumns() {
        // A business card: name on the left, address on the right, rows aligned.
        val lines = listOf(
            line("Musterfirma GmbH", y = 0.30, height = 0.05, width = 0.35),
            line("Dokumente Scannen", y = 0.37, height = 0.05, width = 0.35),
            line("Beispielstraße 1", y = 0.30, height = 0.05, x = 0.55, width = 0.35),
            line("00000 Musterstadt", y = 0.37, height = 0.05, x = 0.55, width = 0.35),
        )

        assertEquals(lines.map { it.text }, Layout.mergeTableColumns(lines).map { it.text })
    }

    @Test
    fun numericCells() {
        assertTrue(Layout.isNumeric("3,27"))
        assertTrue(Layout.isNumeric("30,00 €"))
        assertTrue(Layout.isNumeric("2"))
        assertFalse(Layout.isNumeric("Beispielstraße 1"))
        assertFalse(Layout.isNumeric("Einzelpreis"))
        assertFalse(Layout.isNumeric(" "))
    }

    @Test
    fun readingOrderGoesColumnByColumn() {
        // ML Kit's order on a turned page: rows interleaved, bottom first.
        val lines = listOf(
            line("total", y = 0.70, height = 0.04),
            line("right two", y = 0.35, height = 0.04, x = 0.6, width = 0.3),
            line("left two", y = 0.35, height = 0.04, width = 0.3),
            line("right one", y = 0.30, height = 0.04, x = 0.6, width = 0.3),
            line("left one", y = 0.30, height = 0.04, width = 0.3),
            line("Title", y = 0.10, height = 0.06),
        )

        assertEquals(
            listOf("Title", "left one", "left two", "right one", "right two", "total"),
            Layout.readingOrder(lines).map { it.text },
        )
    }

    @Test
    fun joinsFragmentsOfOneLine() {
        // A 1600 × 1000 frame: a 0.01 gap is 16 px, a 0.05 height is 50 px.
        val lines = listOf(
            line("brown fox", y = 0.30, height = 0.05, x = 0.31, width = 0.14),
            line("The quick", y = 0.301, height = 0.05, x = 0.10, width = 0.20),
            line("3,40", y = 0.30, height = 0.05, x = 0.80, width = 0.06),
            line("next row", y = 0.40, height = 0.05, x = 0.46, width = 0.2),
        )

        val joined = Layout.joinFragments(lines, aspect = 1.6)

        assertEquals(listOf("The quick brown fox", "next row", "3,40"), joined.map { it.text })
        assertEquals(listOf("The", "quick", "brown", "fox"), joined[0].words.map { it.text })
        assertEquals(0.45, joined[0].box.maxX, 1e-9)
    }

    @Test
    fun resolvesLanguagesToSupportedTags() {
        val supported = listOf("en", "de", "sr-Latn", "zh-Hans", "zh-Hant")

        assertEquals(listOf("de", "en"), Languages.resolve(listOf("de-DE", "en-US"), supported))
        assertEquals(listOf("de", "en"), Languages.resolve(listOf("de", "en_gb", "DE-at"), supported))
        assertEquals(listOf("zh-Hant"), Languages.resolve(listOf("zh-Hant"), supported))
        assertEquals(listOf("zh-Hans"), Languages.resolve(listOf("zh"), supported))
        assertEquals(listOf("sr-Latn"), Languages.resolve(listOf("sr"), supported))
        try {
            Languages.resolve(listOf("he-IL"), supported)
            fail("expected unsupported-language")
        } catch (error: OcrException) {
            assertEquals("unsupported-language", error.code)
        }
    }

    @Test
    fun scriptModelsAddTheirLanguages() {
        val latinOnly = Languages.supported(emptySet())
        assertTrue(latinOnly.containsAll(listOf("en", "de", "fr", "pl", "tr")))
        assertFalse(latinOnly.contains("ja"))

        val withJapanese = Languages.supported(setOf(Script.JAPANESE))
        assertTrue(withJapanese.contains("ja"))
        assertFalse(withJapanese.contains("ko"))
    }

    @Test
    fun picksTheModelForTheRequestedScript() {
        assertEquals(Script.LATIN, Languages.scriptFor(emptyList()))
        assertEquals(Script.LATIN, Languages.scriptFor(listOf("de", "en")))
        // Script models read Latin text too, so a mix uses the script's model.
        assertEquals(Script.JAPANESE, Languages.scriptFor(listOf("en", "ja")))
        assertEquals(Script.CHINESE, Languages.scriptFor(listOf("zh-Hant")))
    }

    @Test
    fun fileSystemPathFromEachInputForm() {
        assertEquals("/data/user/0/a b.jpg", ImageLoader.fileSystemPath("/data/user/0/a b.jpg"))
        assertEquals("/data/user/0/a b.jpg", ImageLoader.fileSystemPath("file:///data/user/0/a%20b.jpg"))
        assertEquals(
            "/data/user/0/a b+c.jpg",
            ImageLoader.fileSystemPath("https://localhost/_capacitor_file_/data/user/0/a%20b+c.jpg"),
        )
    }

    @Test
    fun stripsDataUrlPrefix() {
        assertEquals("iVBOR", ImageLoader.stripDataUrlPrefix("data:image/png;base64,iVBOR"))
        assertEquals("iVBOR", ImageLoader.stripDataUrlPrefix("iVBOR"))
    }

    private fun line(text: String, y: Double, height: Double, x: Double = 0.1, width: Double = 0.4): OcrLine {
        val box = Box(x, y, width, height)
        val words = text.split(" ").map { OcrWord(it, box, 1.0) }
        return OcrLine(text, box, 1.0, words)
    }
}

class TextOrientationTest {
    @Test
    fun dominantDirectionWeighsLongLines() {
        assertEquals(TextOrientation.UP, TextOrientation.dominant(listOf(100.0 to 2.0, -10.0 to 0.0)))
        assertEquals(TextOrientation.RUNS_DOWN, TextOrientation.dominant(listOf(3.0 to 200.0)))
        assertEquals(TextOrientation.RUNS_UP, TextOrientation.dominant(listOf(0.0 to -150.0, 20.0 to 0.0)))
        assertEquals(TextOrientation.DOWN, TextOrientation.dominant(listOf(-80.0 to 1.0)))
        assertEquals(TextOrientation.UP, TextOrientation.dominant(emptyList()))
    }

    @Test
    fun rotationTurnsTheImageUpright() {
        assertEquals(0, TextOrientation.UP.rotation)
        // Text running top to bottom: the page lies a quarter clockwise, so turn it 270° more.
        assertEquals(270, TextOrientation.RUNS_DOWN.rotation)
        assertEquals(180, TextOrientation.DOWN.rotation)
        assertEquals(90, TextOrientation.RUNS_UP.rotation)
    }

    @Test
    fun roundTripsEveryOrientationExactly() {
        val box = Box(0.1, 0.2, 0.3, 0.05)
        for (orientation in TextOrientation.entries) {
            assertTrue("$orientation", orientation.toImage(orientation.toText(box)).isClose(box, tolerance = 1e-12))
        }
    }

    @Test
    fun sidewaysLineBecomesHorizontal() {
        // Text running top to bottom near the right edge: a tall, narrow box.
        val box = Box(0.8, 0.1, 0.05, 0.6)
        val turned = TextOrientation.RUNS_DOWN.toText(box)

        assertTrue(turned.width > turned.height)
        // The right-hand column is the first line once the page is upright.
        assertEquals(0.15, turned.y, 1e-9)
    }
}
