import XCTest
@testable import NativeOcrPlugin

final class ImageLoaderTests: XCTestCase {
    func testFileSystemPathFromEachInputForm() {
        XCTAssertEqual(ImageLoader.fileSystemPath(from: "/var/mobile/a b.jpg"), "/var/mobile/a b.jpg")
        XCTAssertEqual(ImageLoader.fileSystemPath(from: "file:///var/mobile/a%20b.jpg"), "/var/mobile/a b.jpg")
        XCTAssertEqual(
            ImageLoader.fileSystemPath(from: "capacitor://localhost/_capacitor_file_/var/mobile/a%20b.jpg"),
            "/var/mobile/a b.jpg"
        )
    }

    func testLoadsBase64WithAndWithoutDataUrlPrefix() throws {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let png = UIGraphicsImageRenderer(size: CGSize(width: 30, height: 20), format: format).pngData { _ in }
        let base64 = png.base64EncodedString()

        let plain = try ImageLoader.load(base64: base64)
        let dataUrl = try ImageLoader.load(base64: "data:image/png;base64," + base64)

        XCTAssertEqual(plain.orientedSize, CGSize(width: 30, height: 20))
        XCTAssertEqual(dataUrl.orientedSize, CGSize(width: 30, height: 20))
        XCTAssertEqual(plain.orientation, .up)
    }

    func testRejectsMissingFileAndUndecodableData() {
        XCTAssertThrowsError(try ImageLoader.load(path: "/no/such/file.jpg")) { error in
            XCTAssertEqual((error as? OcrError)?.code, "invalid-image")
        }
        XCTAssertThrowsError(try ImageLoader.load(base64: Data("not an image".utf8).base64EncodedString())) { error in
            XCTAssertEqual((error as? OcrError)?.code, "invalid-image")
        }
        XCTAssertThrowsError(try ImageLoader.load(base64: "%%%")) { error in
            XCTAssertEqual((error as? OcrError)?.code, "invalid-image")
        }
    }
}
