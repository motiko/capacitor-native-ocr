package dev.motiko.nativeocr

import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutionException

data class RecognizeOptions(
    val languages: List<String> = emptyList(),
    /**
     * Join table columns into rows (`Layout.mergeTableColumns`). Not exposed to JavaScript;
     * the benchmark turns it off to measure its effect.
     */
    val tableRows: Boolean = true,
)

data class RecognizeResult(
    val text: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<OcrBlock>,
    /** Clockwise degrees that turn the image upright. */
    val rotation: Int,
    val language: String?,
)

/**
 * Text recognition with ML Kit. Knows nothing about Capacitor, so it can be tested directly.
 * Call it off the main thread: [recognize] blocks until ML Kit is done.
 */
class NativeOcr(private val enabledScripts: Set<Script> = buildScripts()) : AutoCloseable {
    private val recognizers = mutableMapOf<Script, TextRecognizer>()

    fun supportedLanguages(): List<String> = Languages.supported(enabledScripts)

    fun recognize(image: LoadedImage, options: RecognizeOptions = RecognizeOptions()): RecognizeResult {
        val languages = if (options.languages.isEmpty()) {
            emptyList()
        } else {
            Languages.resolve(options.languages, supportedLanguages())
        }
        val result = try {
            Tasks.await(recognizer(Languages.scriptFor(languages)).process(InputImage.fromBitmap(image.bitmap, 0)))
        } catch (error: ExecutionException) {
            throw OcrException.RecognitionFailed(error.cause?.message ?: error.message ?: "ML Kit failed.")
        } catch (error: InterruptedException) {
            throw OcrException.RecognitionFailed("Recognition was interrupted.")
        }

        val width = image.width
        val height = image.height
        val mlLines = result.textBlocks.flatMap { it.lines }
        val orientation = TextOrientation.dominant(mlLines.mapNotNull(::baseline))
        // Layout runs in the text's frame, so a sideways page groups and orders like an upright one.
        // ML Kit orders blocks by their place in the image, not in the text, and sometimes splits
        // a sideways line in pieces, so lines are joined and ordered here rather than taken as is.
        val lines = mlLines.mapNotNull { line(it, width, height, orientation) }.map(orientation::toText)
        val aspect = if (orientation.isSideways) height.toDouble() / width else width.toDouble() / height
        val ordered = Layout.readingOrder(Layout.joinFragments(lines, aspect))
        val blocks = Layout.groupIntoBlocks(if (options.tableRows) Layout.mergeTableColumns(ordered) else ordered)
            .map(orientation::toImage)
        return RecognizeResult(
            text = Layout.text(blocks),
            imageWidth = width,
            imageHeight = height,
            blocks = blocks,
            rotation = orientation.rotation,
            language = dominantLanguage(mlLines),
        )
    }

    override fun close() {
        synchronized(recognizers) {
            recognizers.values.forEach { it.close() }
            recognizers.clear()
        }
    }

    private fun recognizer(script: Script): TextRecognizer = synchronized(recognizers) {
        recognizers.getOrPut(script) {
            // Each branch touches its model's classes only when that model is linked in.
            when (script) {
                Script.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                Script.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                Script.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                Script.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                Script.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            }
        }
    }

    companion object {
        /** The script models the app enabled with Gradle flags (see build.gradle). */
        fun buildScripts(): Set<Script> = buildSet {
            if (BuildConfig.OCR_CHINESE) add(Script.CHINESE)
            if (BuildConfig.OCR_DEVANAGARI) add(Script.DEVANAGARI)
            if (BuildConfig.OCR_JAPANESE) add(Script.JAPANESE)
            if (BuildConfig.OCR_KOREAN) add(Script.KOREAN)
        }

        /** The line's top edge in pixels, from its first corner to its second, in reading direction. */
        private fun baseline(line: Text.Line): Pair<Double, Double>? {
            val corners = line.cornerPoints ?: return null
            if (corners.size < 2) return null
            return (corners[1].x - corners[0].x).toDouble() to (corners[1].y - corners[0].y).toDouble()
        }

        private fun box(rect: Rect, width: Int, height: Int): Box = Box.fromPixels(
            rect.left.toDouble(), rect.top.toDouble(), rect.right.toDouble(), rect.bottom.toDouble(), width, height,
        )

        private fun line(line: Text.Line, width: Int, height: Int, orientation: TextOrientation): OcrLine? {
            val text = line.text
            val rect = line.boundingBox ?: return null
            val lineBox = box(rect, width, height)
            val confidence = line.confidence.toDouble().takeIf { it > 0 }

            val elements = line.elements.filter { it.text.isNotBlank() }
            val words = if (elements.isNotEmpty()) {
                elements.map { element ->
                    OcrWord(
                        text = element.text,
                        box = element.boundingBox?.let { box(it, width, height) }
                            ?: lineBox,
                        confidence = element.confidence.toDouble().takeIf { it > 0 } ?: confidence,
                    )
                }
            } else {
                Layout.wordRanges(text).map { range ->
                    OcrWord(
                        text = text.substring(range),
                        box = orientation.toImage(Layout.estimatedWordBox(range, text, orientation.toText(lineBox))),
                        confidence = confidence,
                    )
                }
            }
            if (words.isEmpty()) return null
            return OcrLine(text = words.joinToString(" ") { it.text }, box = lineBox, confidence = confidence, words = words)
        }

        /** The language most of the text is in, weighted by line length; undetermined (`und`, `und-Latn`) doesn't count. */
        private fun dominantLanguage(lines: List<Text.Line>): String? {
            val votes = mutableMapOf<String, Int>()
            for (line in lines) {
                val language = line.recognizedLanguage
                if (language.isBlank() || language.startsWith("und")) continue
                votes[language] = (votes[language] ?: 0) + line.text.length
            }
            return votes.maxByOrNull { it.value }?.key
        }
    }
}
