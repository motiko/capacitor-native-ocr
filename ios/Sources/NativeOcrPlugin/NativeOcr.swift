import Foundation
import NaturalLanguage
import Vision

struct RecognizeOptions {
    var languages: [String] = []
    /// `nil` means the default: detect when no languages are given.
    var detectLanguage: Bool?
    var level: VNRequestTextRecognitionLevel = .accurate
    var languageCorrection = true
    var customWords: [String] = []
    /// Join table columns into rows (`Layout.mergeTableColumns`). Not exposed to JavaScript;
    /// the benchmark turns it off to measure its effect.
    var tableRows = true
}

struct RecognizeResult {
    var text: String
    var imageSize: CGSize
    var blocks: [OcrBlock]
    var language: String?
}

/// Text recognition with Apple Vision. Knows nothing about Capacitor, so it can be tested directly.
@objc public class NativeOcr: NSObject {
    func supportedLanguages(level: VNRequestTextRecognitionLevel) throws -> [String] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = level
        do {
            return try request.supportedRecognitionLanguages()
        } catch {
            throw OcrError.recognitionFailed("Can't list supported languages: \(error.localizedDescription)")
        }
    }

    func recognize(_ image: LoadedImage, options: RecognizeOptions) throws -> RecognizeResult {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = options.level
        request.usesLanguageCorrection = options.languageCorrection
        request.customWords = options.customWords

        if !options.languages.isEmpty {
            let supported = try supportedLanguages(level: options.level)
            request.recognitionLanguages = try Self.resolveLanguages(options.languages, supported: supported)
        }
        if #available(iOS 16.0, *) {
            request.automaticallyDetectsLanguage = options.detectLanguage ?? options.languages.isEmpty
        }

        let handler = VNImageRequestHandler(cgImage: image.cgImage, orientation: image.orientation, options: [:])
        do {
            try handler.perform([request])
        } catch {
            throw OcrError.recognitionFailed(error.localizedDescription)
        }

        // With an orientation given, Vision reports coordinates in the upright image.
        let observations = request.results ?? []
        let size = image.orientedSize
        let orientation = TextOrientation.dominant(baselines: observations.map { observation in
            (dx: Double(observation.topRight.x - observation.topLeft.x) * size.width,
             dy: -Double(observation.topRight.y - observation.topLeft.y) * size.height)
        })
        // Layout runs in the text's frame, so a sideways page groups and orders like an upright one.
        let lines = observations.compactMap { Self.line(from: $0, orientation: orientation) }.map(orientation.toText)
        let blocks = Layout.groupIntoBlocks(options.tableRows ? Layout.mergeTableColumns(lines) : lines)
            .map(orientation.toImage)
        let text = Layout.text(of: blocks)
        return RecognizeResult(
            text: text,
            imageSize: image.orientedSize,
            blocks: blocks,
            language: Self.dominantLanguage(of: text)
        )
    }

    /// Maps requested tags to Vision's identifiers. A tag Vision doesn't list as written
    /// falls back to the first supported tag for the same language: `de` or `de-AT` → `de-DE`.
    static func resolveLanguages(_ requested: [String], supported: [String]) throws -> [String] {
        func normalized(_ tag: String) -> String {
            tag.replacingOccurrences(of: "_", with: "-").lowercased()
        }
        func baseLanguage(_ tag: String) -> String {
            String(normalized(tag).split(separator: "-").first ?? "")
        }

        var resolved: [String] = []
        for tag in requested {
            let match = supported.first { normalized($0) == normalized(tag) }
                ?? supported.first { baseLanguage($0) == baseLanguage(tag) }
            guard let match else {
                throw OcrError.unsupportedLanguage("Language \(tag) is not supported. Supported: \(supported.joined(separator: ", ")).")
            }
            if !resolved.contains(match) {
                resolved.append(match)
            }
        }
        return resolved
    }

    private static func line(from observation: VNRecognizedTextObservation, orientation: TextOrientation) -> OcrLine? {
        guard let candidate = observation.topCandidates(1).first else { return nil }
        let text = candidate.string
        let lineBox = Box(visionRect: observation.boundingBox)
        let confidence = Double(candidate.confidence)

        let ranges = Layout.wordRanges(in: text)
        guard !ranges.isEmpty else { return nil }

        let words = ranges.map { range -> OcrWord in
            var box = (try? candidate.boundingBox(for: range)).map { Box(visionRect: $0.boundingBox) }
            // Vision sometimes answers a word's range with the whole line's box.
            if ranges.count > 1, let found = box, found.isClose(to: lineBox) {
                box = nil
            }
            return OcrWord(
                text: String(text[range]),
                box: box ?? orientation.toImage(
                    Layout.estimatedWordBox(for: range, in: text, lineBox: orientation.toText(lineBox))
                ),
                confidence: confidence
            )
        }
        return OcrLine(text: text, box: lineBox, confidence: confidence, words: words)
    }

    private static func dominantLanguage(of text: String) -> String? {
        guard !text.isEmpty else { return nil }
        let language = NLLanguageRecognizer.dominantLanguage(for: text)
        return language == .undetermined ? nil : language?.rawValue
    }
}
