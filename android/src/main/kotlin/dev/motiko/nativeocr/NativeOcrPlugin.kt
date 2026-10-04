package dev.motiko.nativeocr

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Android support (ML Kit Text Recognition v2) is planned for a later 0.x release.
 * Until then the plugin reports itself unavailable, so apps fall back to their own engine.
 */
@CapacitorPlugin(name = "NativeOcr")
class NativeOcrPlugin : Plugin() {

    @PluginMethod
    fun isAvailable(call: PluginCall) {
        call.resolve(JSObject().put("available", false))
    }

    @PluginMethod
    fun getSupportedLanguages(call: PluginCall) {
        rejectUnavailable(call)
    }

    @PluginMethod
    fun recognize(call: PluginCall) {
        rejectUnavailable(call)
    }

    private fun rejectUnavailable(call: PluginCall) {
        call.reject("Native OCR is not available on Android yet.", "unavailable")
    }
}
