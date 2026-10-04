package dev.motiko.nativeocr

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@CapacitorPlugin(name = "NativeOcr")
class NativeOcrPlugin : Plugin() {
    private val implementation = NativeOcr()

    /** One recognition at a time, off the main thread; ML Kit is fastest without contention. */
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @PluginMethod
    fun isAvailable(call: PluginCall) {
        call.resolve(JSObject().put("available", true))
    }

    /** ML Kit has one recognition level, so `level` doesn't change the list. */
    @PluginMethod
    fun getSupportedLanguages(call: PluginCall) {
        call.resolve(JSObject().put("languages", JSArray(implementation.supportedLanguages())))
    }

    /** `detectLanguage`, `level`, `languageCorrection` and `customWords` have no ML Kit counterpart. */
    @PluginMethod
    fun recognize(call: PluginCall) {
        val languages = call.getArray("languages")?.toList<Any>()?.map { it.toString() } ?: emptyList()
        val path = call.getString("path")
        val base64 = call.getString("base64")

        executor.execute {
            try {
                val image = when {
                    !path.isNullOrEmpty() -> ImageLoader.load(context, path)
                    !base64.isNullOrEmpty() -> ImageLoader.load(base64)
                    else -> throw OcrException.InvalidImage("Pass either path or base64.")
                }
                val result = try {
                    implementation.recognize(image, RecognizeOptions(languages = languages))
                } finally {
                    image.bitmap.recycle()
                }
                call.resolve(json(result))
            } catch (error: OcrException) {
                call.reject(error.message, error.code)
            } catch (error: OutOfMemoryError) {
                call.reject("The image is too large to decode.", "invalid-image")
            } catch (error: Exception) {
                call.reject(error.message ?: "Recognition failed.", "recognition-failed", error)
            }
        }
    }

    override fun handleOnDestroy() {
        executor.shutdown()
        implementation.close()
        super.handleOnDestroy()
    }

    companion object {
        fun json(result: RecognizeResult): JSObject {
            val json = JSObject()
            json.put("text", result.text)
            json.put("imageSize", JSObject().put("width", result.imageWidth).put("height", result.imageHeight))
            json.put("blocks", JSArray(result.blocks.map(::json)))
            json.put("rotation", result.rotation)
            result.language?.let { json.put("language", it) }
            return json
        }

        private fun json(block: OcrBlock): JSObject =
            JSObject().put("text", block.text).put("box", json(block.box)).put("lines", JSArray(block.lines.map(::json)))

        private fun json(line: OcrLine): JSObject {
            val json = JSObject().put("text", line.text).put("box", json(line.box)).put("words", JSArray(line.words.map(::json)))
            line.confidence?.let { json.put("confidence", it) }
            return json
        }

        private fun json(word: OcrWord): JSObject {
            val json = JSObject().put("text", word.text).put("box", json(word.box))
            word.confidence?.let { json.put("confidence", it) }
            return json
        }

        private fun json(box: Box): JSObject =
            JSObject().put("x", box.x).put("y", box.y).put("width", box.width).put("height", box.height)
    }
}
