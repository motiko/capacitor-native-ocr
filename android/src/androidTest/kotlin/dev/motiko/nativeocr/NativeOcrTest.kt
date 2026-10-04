package dev.motiko.nativeocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Recognition on the iOS fixtures from scripts/make-fixtures.swift (1600 × 1000 px), which
 * build.gradle adds to the test assets.
 */
@RunWith(AndroidJUnit4::class)
class NativeOcrTest {
    private val tolerance = 0.02
    private val receiptText = """
        MUSTERMARKT

        Roggenbrot 750g 3,40
        Mineralwasser 6x1L 3,54
        Bananen 1,76
        Joghurt Natur 0,79

        SUMME EUR 9,49
    """.trimIndent()

    @Test
    fun recognizesEnglishPrintInBlocks() {
        val result = ocr.recognize(fixture("print-en.png"), RecognizeOptions(languages = listOf("en-US")))

        assertEquals(1600 to 1000, result.imageWidth to result.imageHeight)
        assertEquals(listOf(1, 2, 1), result.blocks.map { it.lines.size })
        assertEquals(
            """
            Invoice 2026-104

            The quick brown fox jumps over
            the lazy dog near the river bank.

            Total: 1,234.56 EUR
            """.trimIndent(),
            result.text,
        )
        assertEquals("en", result.language)
        assertEquals(0, result.rotation)
    }

    @Test
    fun wordBoxesMatchDrawnPositions() {
        val result = ocr.recognize(fixture("print-en.png"), RecognizeOptions(languages = listOf("en-US")))
        val words = result.blocks.flatMap { it.lines }.flatMap { it.words }

        // "Invoice" is drawn from x 100 to 410 px, cap height from y 100 to 178 px.
        val invoice = words.first { it.text == "Invoice" }
        assertBox(invoice.box, left = 100 / 1600.0, top = 100 / 1000.0, right = 410 / 1600.0, bottom = 178 / 1000.0)

        // "EUR" ends the last line, from x 465 to 568 px, y 712 to 757 px.
        val eur = words.first { it.text == "EUR" }
        assertBox(eur.box, left = 465 / 1600.0, top = 712 / 1000.0, right = 568 / 1600.0, bottom = 757 / 1000.0)

        // Word boxes are smaller than their line and inside it. Unlike Vision, ML Kit scores
        // each word on its own.
        val line = result.blocks[0].lines[0]
        assertEquals(listOf("Invoice", "2026-104"), line.words.map { it.text })
        for (word in line.words) {
            assertTrue(word.box.width < line.box.width)
            assertTrue(word.box.x >= line.box.x - tolerance)
            assertTrue(word.box.maxX <= line.box.maxX + tolerance)
            val confidence = word.confidence
            assertNotNull(confidence)
            assertTrue(confidence!! in 0.0..1.0)
        }
    }

    @Test
    fun appliesExifOrientation() {
        val upright = ocr.recognize(fixture("print-en.png"), RecognizeOptions(languages = listOf("en-US")))
        val rotated = ocr.recognize(fixture("print-en-exif6.jpg"), RecognizeOptions(languages = listOf("en-US")))

        assertEquals(1600 to 1000, rotated.imageWidth to rotated.imageHeight)
        assertEquals(upright.text, rotated.text)
        assertEquals("EXIF already turns it upright", 0, rotated.rotation)
        val uprightLines = upright.blocks.flatMap { it.lines }
        val rotatedLines = rotated.blocks.flatMap { it.lines }
        // iOS holds these to 0.01. ML Kit's line edges move a little more between the PNG and
        // the JPEG copy (up to about 20 px of 1600 on the second line), so allow the word tolerance.
        for ((a, b) in uprightLines.zip(rotatedLines)) {
            assertTrue("${a.box} vs ${b.box}", a.box.isClose(b.box, tolerance = tolerance))
        }
    }

    @Test
    fun readsReceiptRowByRow() {
        val result = ocr.recognize(fixture("receipt.png"), RecognizeOptions(languages = listOf("de-DE")))

        assertEquals(receiptText, result.text)
        assertEquals(0, result.rotation)
    }

    @Test
    fun readsSidewaysReceiptLikeAnUprightOne() {
        // The fixture has no EXIF tag; the pixels are turned a quarter counter-clockwise.
        val sideways = ocr.recognize(fixture("receipt-sideways.png"), RecognizeOptions(languages = listOf("de-DE")))

        assertEquals(receiptText, sideways.text)
        assertEquals(1000 to 1600, sideways.imageWidth to sideways.imageHeight)
        assertEquals(90, sideways.rotation)
        // Boxes stay in image coordinates: sideways lines are tall and narrow.
        val line = sideways.blocks.first().lines.first()
        assertTrue(line.box.height > line.box.width)
    }

    @Test
    fun readsUpsideDownReceiptLikeAnUprightOne() {
        val upsideDown = ocr.recognize(fixture("receipt-upside-down.png"), RecognizeOptions(languages = listOf("de-DE")))

        assertEquals(receiptText, upsideDown.text)
        assertEquals(180, upsideDown.rotation)
        // Boxes stay in image coordinates: the heading, first in reading order, is at the bottom.
        assertTrue(upsideDown.blocks.first().lines.first().box.y > 0.5)
    }

    @Test
    fun reportsQuarterTurnClockwise() {
        // Turned a quarter clockwise, the text runs top to bottom: turning it back is 270°.
        val result = ocr.recognize(turned(fixture("receipt.png"), 90), RecognizeOptions(languages = listOf("de-DE")))

        assertEquals(receiptText, result.text)
        assertEquals(270, result.rotation)
    }

    @Test
    fun joinsSidewaysLineFragments() {
        // Turned a quarter counter-clockwise, ML Kit splits "the lazy dog near the river bank."
        // in two; the halves are joined again.
        val upright = ocr.recognize(fixture("print-en.png"), RecognizeOptions(languages = listOf("en-US")))
        val sideways = ocr.recognize(turned(fixture("print-en.png"), 270), RecognizeOptions(languages = listOf("en-US")))

        assertEquals(upright.text, sideways.text)
        assertEquals(upright.blocks.map { it.lines.size }, sideways.blocks.map { it.lines.size })
        assertEquals(90, sideways.rotation)
    }

    @Test
    fun recognizesGermanUmlauts() {
        val result = ocr.recognize(fixture("print-de.png"), RecognizeOptions(languages = listOf("de-DE")))

        assertEquals("Größere Änderungen für Übermorgen\nStraße und Gebühren", result.text)
        assertEquals("de", result.language)
    }

    @Test
    fun recognizesWithoutLanguageHint() {
        val result = ocr.recognize(fixture("print-de.png"))

        assertTrue(result.text, result.text.contains("Straße"))
    }

    @Test
    fun rejectsUnsupportedLanguage() {
        try {
            ocr.recognize(fixture("print-en.png"), RecognizeOptions(languages = listOf("tlh-QO")))
            fail("expected unsupported-language")
        } catch (error: OcrException) {
            assertEquals("unsupported-language", error.code)
        }
    }

    @Test
    fun supportedLanguagesIncludeEnglishAndGerman() {
        val languages = ocr.supportedLanguages()

        assertTrue(languages.contains("en"))
        assertTrue(languages.contains("de"))
    }

    @Test
    fun blankImageGivesEmptyResult() {
        val blank = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val result = ocr.recognize(ImageLoader.load(Base64.encodeToString(png(blank), Base64.NO_WRAP)))

        assertEquals("", result.text)
        assertTrue(result.blocks.isEmpty())
        assertNull(result.language)
        assertEquals(0, result.rotation)
    }

    @Test
    fun loadsEachPathForm() {
        val file = File(context.cacheDir, "a b.png")
        file.writeBytes(asset("print-en.png"))

        for (path in listOf(
            file.path,
            "file://" + file.path.replace(" ", "%20"),
            "https://localhost/_capacitor_file_" + file.path.replace(" ", "%20"),
        )) {
            val image = ImageLoader.load(context, path)
            assertEquals(path, 1600 to 1000, image.width to image.height)
        }
    }

    @Test
    fun loadsBase64WithAndWithoutDataUrlPrefix() {
        val base64 = Base64.encodeToString(png(Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888)), Base64.NO_WRAP)

        val plain = ImageLoader.load(base64)
        val dataUrl = ImageLoader.load("data:image/png;base64,$base64")

        assertEquals(30 to 20, plain.width to plain.height)
        assertEquals(30 to 20, dataUrl.width to dataUrl.height)
    }

    @Test
    fun rejectsMissingFileAndUndecodableData() {
        val attempts = listOf(
            { ImageLoader.load(context, "/no/such/file.jpg") },
            { ImageLoader.load(Base64.encodeToString("not an image".toByteArray(), Base64.NO_WRAP)) },
            { ImageLoader.load("%%%") },
        )
        for (attempt in attempts) {
            try {
                attempt()
                fail("expected invalid-image")
            } catch (error: OcrException) {
                assertEquals("invalid-image", error.code)
            }
        }
    }

    @Test
    fun pluginJsonCarriesEveryField() {
        val result = ocr.recognize(fixture("print-en.png"), RecognizeOptions(languages = listOf("en")))
        val json = NativeOcrPlugin.json(result)

        assertEquals(result.text, json.getString("text"))
        assertEquals(0, json.getInt("rotation"))
        assertEquals("en", json.getString("language"))
        assertEquals(1600, json.getJSONObject("imageSize").getInt("width"))
        val word = json.getJSONArray("blocks").getJSONObject(0).getJSONArray("lines").getJSONObject(0)
            .getJSONArray("words").getJSONObject(0)
        assertEquals("Invoice", word.getString("text"))
        assertTrue(word.getJSONObject("box").getDouble("width") > 0)
    }

    // Helpers

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun asset(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }

    private fun fixture(name: String): LoadedImage =
        ImageLoader.load(Base64.encodeToString(asset(name), Base64.NO_WRAP))

    /** The image turned clockwise, as a camera held the other way would store it. */
    private fun turned(image: LoadedImage, degrees: Int): LoadedImage {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return LoadedImage(Bitmap.createBitmap(image.bitmap, 0, 0, image.width, image.height, matrix, true))
    }

    private fun png(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun assertBox(box: Box, left: Double, top: Double, right: Double, bottom: Double) {
        assertEquals("left of $box", left, box.x, tolerance)
        assertEquals("right of $box", right, box.maxX, tolerance)
        assertEquals("top of $box", top, box.y, tolerance * 2)
        assertEquals("bottom of $box", bottom, box.maxY, tolerance * 2)
    }

    companion object {
        private val ocr = NativeOcr()

        @JvmStatic
        @AfterClass
        fun closeRecognizers() {
            ocr.close()
        }
    }
}
