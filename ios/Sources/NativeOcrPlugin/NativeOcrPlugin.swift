import Capacitor
import Foundation
import Vision

@objc(NativeOcrPlugin)
public class NativeOcrPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "NativeOcrPlugin"
    public let jsName = "NativeOcr"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "isAvailable", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSupportedLanguages", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "recognize", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = NativeOcr()

    @objc func isAvailable(_ call: CAPPluginCall) {
        call.resolve(["available": true])
    }

    @objc func getSupportedLanguages(_ call: CAPPluginCall) {
        let level = recognitionLevel(from: call)
        do {
            call.resolve(["languages": try implementation.supportedLanguages(level: level)])
        } catch {
            reject(call, error)
        }
    }

    @objc func recognize(_ call: CAPPluginCall) {
        let level = recognitionLevel(from: call)
        let options = RecognizeOptions(
            languages: call.getArray("languages", String.self) ?? [],
            detectLanguage: call.getBool("detectLanguage"),
            level: level,
            languageCorrection: call.getBool("languageCorrection") ?? true,
            customWords: call.getArray("customWords", String.self) ?? []
        )
        let path = call.getString("path")
        let base64 = call.getString("base64")

        DispatchQueue.global(qos: .userInitiated).async { [implementation] in
            do {
                let image: LoadedImage
                if let path, !path.isEmpty {
                    image = try ImageLoader.load(path: path)
                } else if let base64, !base64.isEmpty {
                    image = try ImageLoader.load(base64: base64)
                } else {
                    throw OcrError.invalidImage("Pass either path or base64.")
                }
                let result = try implementation.recognize(image, options: options)
                call.resolve(Self.json(result))
            } catch {
                self.reject(call, error)
            }
        }
    }

    private func recognitionLevel(from call: CAPPluginCall) -> VNRequestTextRecognitionLevel {
        call.getString("level") == "fast" ? .fast : .accurate
    }

    private func reject(_ call: CAPPluginCall, _ error: Error) {
        if let error = error as? OcrError {
            call.reject(error.message, error.code)
        } else {
            call.reject(error.localizedDescription, "recognition-failed", error)
        }
    }

    // MARK: - JSON

    static func json(_ result: RecognizeResult) -> JSObject {
        var object: JSObject = [
            "text": result.text,
            "imageSize": ["width": Double(result.imageSize.width), "height": Double(result.imageSize.height)] as JSObject,
            "blocks": result.blocks.map(json) as JSArray
        ]
        if let language = result.language {
            object["language"] = language
        }
        return object
    }

    private static func json(_ block: OcrBlock) -> JSObject {
        ["text": block.text, "box": json(block.box), "lines": block.lines.map(json) as JSArray]
    }

    private static func json(_ line: OcrLine) -> JSObject {
        var object: JSObject = ["text": line.text, "box": json(line.box), "words": line.words.map(json) as JSArray]
        if let confidence = line.confidence {
            object["confidence"] = confidence
        }
        return object
    }

    private static func json(_ word: OcrWord) -> JSObject {
        var object: JSObject = ["text": word.text, "box": json(word.box)]
        if let confidence = word.confidence {
            object["confidence"] = confidence
        }
        return object
    }

    private static func json(_ box: Box) -> JSObject {
        ["x": box.x, "y": box.y, "width": box.width, "height": box.height]
    }
}
