import Foundation
import ImageIO

/// A decoded image plus the EXIF orientation that Vision should apply to it.
struct LoadedImage {
    let cgImage: CGImage
    let orientation: CGImagePropertyOrientation

    /// Pixel size after the orientation is applied.
    var orientedSize: CGSize {
        let width = CGFloat(cgImage.width)
        let height = CGFloat(cgImage.height)
        switch orientation {
        case .left, .leftMirrored, .right, .rightMirrored:
            return CGSize(width: height, height: width)
        default:
            return CGSize(width: width, height: height)
        }
    }
}

enum ImageLoader {
    /// Marker in URLs made by `Capacitor.convertFileSrc()`, e.g.
    /// `capacitor://localhost/_capacitor_file_/var/mobile/...`.
    private static let capacitorFileMarker = "/_capacitor_file_"

    static func load(path: String) throws -> LoadedImage {
        let filePath = fileSystemPath(from: path)
        guard FileManager.default.fileExists(atPath: filePath) else {
            throw OcrError.invalidImage("No file at \(path).")
        }
        let url = URL(fileURLWithPath: filePath)
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else {
            throw OcrError.invalidImage("Can't read the image at \(path).")
        }
        return try decode(source)
    }

    static func load(base64: String) throws -> LoadedImage {
        var payload = Substring(base64)
        if payload.hasPrefix("data:"), let comma = payload.firstIndex(of: ",") {
            payload = payload[payload.index(after: comma)...]
        }
        guard let data = Data(base64Encoded: String(payload), options: .ignoreUnknownCharacters),
              let source = CGImageSourceCreateWithData(data as CFData, nil)
        else {
            throw OcrError.invalidImage("The base64 data is not a readable image.")
        }
        return try decode(source)
    }

    /// Turns a plain path, a `file://` URL or a `convertFileSrc()` URL into a file system path.
    static func fileSystemPath(from path: String) -> String {
        if let range = path.range(of: capacitorFileMarker) {
            let rest = String(path[range.upperBound...])
            return rest.removingPercentEncoding ?? rest
        }
        if path.hasPrefix("file:"), let url = URL(string: path) {
            return url.path
        }
        return path
    }

    private static func decode(_ source: CGImageSource) throws -> LoadedImage {
        guard CGImageSourceGetCount(source) > 0,
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
        else {
            throw OcrError.invalidImage("The image can't be decoded.")
        }
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
        let rawOrientation = (properties?[kCGImagePropertyOrientation] as? NSNumber)?.uint32Value ?? 1
        let orientation = CGImagePropertyOrientation(rawValue: rawOrientation) ?? .up
        return LoadedImage(cgImage: image, orientation: orientation)
    }
}
