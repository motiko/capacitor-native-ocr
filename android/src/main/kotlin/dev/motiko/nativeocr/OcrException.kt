package dev.motiko.nativeocr

/** Errors with the codes the JavaScript API documents (`NativeOcrErrorCode`). */
sealed class OcrException(val code: String, message: String) : Exception(message) {
    class InvalidImage(message: String) : OcrException("invalid-image", message)

    class UnsupportedLanguage(message: String) : OcrException("unsupported-language", message)

    class RecognitionFailed(message: String) : OcrException("recognition-failed", message)
}
