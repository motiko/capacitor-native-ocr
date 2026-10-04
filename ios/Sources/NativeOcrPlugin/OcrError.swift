import Foundation

/// Errors with the codes the JavaScript API documents (`NativeOcrErrorCode`).
enum OcrError: Error, Equatable {
    case invalidImage(String)
    case unsupportedLanguage(String)
    case recognitionFailed(String)

    var code: String {
        switch self {
        case .invalidImage: return "invalid-image"
        case .unsupportedLanguage: return "unsupported-language"
        case .recognitionFailed: return "recognition-failed"
        }
    }

    var message: String {
        switch self {
        case .invalidImage(let message), .unsupportedLanguage(let message), .recognitionFailed(let message):
            return message
        }
    }
}
