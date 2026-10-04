import XCTest
@testable import NativeOcrPlugin

/// Recognition on the fixtures from scripts/make-fixtures.swift (1600 × 1000 px).
final class NativeOcrTests: XCTestCase {
    private let ocr = NativeOcr()
    private let tolerance = 0.02

    func testRecognizesEnglishPrintInBlocks() throws {
        let result = try ocr.recognize(fixture("print-en.png"), options: RecognizeOptions(languages: ["en-US"]))

        XCTAssertEqual(result.imageSize, CGSize(width: 1600, height: 1000))
        XCTAssertEqual(result.blocks.map(\.lines.count), [1, 2, 1])
        XCTAssertEqual(result.text, """
            Invoice 2026-104

            The quick brown fox jumps over
            the lazy dog near the river bank.

            Total: 1,234.56 EUR
            """)
        XCTAssertEqual(result.language, "en")
        XCTAssertEqual(result.rotation, 0)
    }

    func testWordBoxesMatchDrawnPositions() throws {
        let result = try ocr.recognize(fixture("print-en.png"), options: RecognizeOptions(languages: ["en-US"]))
        let words = result.blocks.flatMap(\.lines).flatMap(\.words)

        // "Invoice" is drawn from x 100 to 410 px, cap height from y 100 to 178 px.
        let invoice = try XCTUnwrap(words.first { $0.text == "Invoice" })
        assertBox(invoice.box, left: 100 / 1600, top: 100 / 1000, right: 410 / 1600, bottom: 178 / 1000)

        // "EUR" ends the last line, from x 465 to 568 px, y 712 to 757 px.
        let eur = try XCTUnwrap(words.first { $0.text == "EUR" })
        assertBox(eur.box, left: 465 / 1600, top: 712 / 1000, right: 568 / 1600, bottom: 757 / 1000)

        // Word boxes are smaller than their line and inside it.
        let line = result.blocks[0].lines[0]
        XCTAssertEqual(line.words.map(\.text), ["Invoice", "2026-104"])
        for word in line.words {
            XCTAssertLessThan(word.box.width, line.box.width)
            XCTAssertGreaterThanOrEqual(word.box.x, line.box.x - tolerance)
            XCTAssertLessThanOrEqual(word.box.maxX, line.box.maxX + tolerance)
            XCTAssertEqual(word.confidence, line.confidence)
        }
    }

    func testAppliesExifOrientation() throws {
        let upright = try ocr.recognize(fixture("print-en.png"), options: RecognizeOptions(languages: ["en-US"]))
        let image = try fixture("print-en-exif6.jpg")
        XCTAssertEqual(image.orientation, .right)

        let rotated = try ocr.recognize(image, options: RecognizeOptions(languages: ["en-US"]))

        XCTAssertEqual(rotated.imageSize, CGSize(width: 1600, height: 1000))
        XCTAssertEqual(rotated.text, upright.text)
        XCTAssertEqual(rotated.rotation, 0, "EXIF already turns it upright")
        let uprightLines = upright.blocks.flatMap(\.lines)
        let rotatedLines = rotated.blocks.flatMap(\.lines)
        for (uprightLine, rotatedLine) in zip(uprightLines, rotatedLines) {
            XCTAssertTrue(uprightLine.box.isClose(to: rotatedLine.box, tolerance: 0.01), "\(uprightLine.box) vs \(rotatedLine.box)")
        }
    }

    func testReadsReceiptRowByRow() throws {
        let result = try ocr.recognize(fixture("receipt.png"), options: RecognizeOptions(languages: ["de-DE"]))

        XCTAssertEqual(result.text, """
            MUSTERMARKT

            Roggenbrot 750g 3,40
            Mineralwasser 6x1L 3,54
            Bananen 1,76
            Joghurt Natur 0,79

            SUMME EUR 9,49
            """)
    }

    func testReadsSidewaysReceiptLikeAnUprightOne() throws {
        let upright = try ocr.recognize(fixture("receipt.png"), options: RecognizeOptions(languages: ["de-DE"]))
        let image = try fixture("receipt-sideways.png")
        XCTAssertEqual(image.orientation, .up, "the fixture has no EXIF tag; the pixels are sideways")

        let sideways = try ocr.recognize(image, options: RecognizeOptions(languages: ["de-DE"]))

        XCTAssertEqual(sideways.text, upright.text)
        XCTAssertEqual(sideways.imageSize, CGSize(width: 1000, height: 1600))
        // Boxes stay in image coordinates: sideways lines are tall and narrow.
        let line = try XCTUnwrap(sideways.blocks.first?.lines.first)
        XCTAssertGreaterThan(line.box.height, line.box.width)
        // The text runs bottom to top: a quarter turn clockwise makes it upright.
        XCTAssertEqual(sideways.rotation, 90)
    }

    func testReadsUpsideDownReceiptLikeAnUprightOne() throws {
        let upright = try ocr.recognize(fixture("receipt.png"), options: RecognizeOptions(languages: ["de-DE"]))

        let upsideDown = try ocr.recognize(fixture("receipt-upside-down.png"), options: RecognizeOptions(languages: ["de-DE"]))

        XCTAssertEqual(upsideDown.text, upright.text)
        XCTAssertEqual(upsideDown.rotation, 180)
        // Boxes stay in image coordinates: the heading, first in reading order, is at the bottom.
        let heading = try XCTUnwrap(upsideDown.blocks.first?.lines.first)
        XCTAssertGreaterThan(heading.box.y, 0.5)
    }

    func testRecognizesGermanUmlauts() throws {
        let result = try ocr.recognize(fixture("print-de.png"), options: RecognizeOptions(languages: ["de-DE"]))

        XCTAssertEqual(result.text, "Größere Änderungen für Übermorgen\nStraße und Gebühren")
        XCTAssertEqual(result.language, "de")
    }

    func testDetectsLanguageWithoutHint() throws {
        let result = try ocr.recognize(fixture("print-de.png"), options: RecognizeOptions())

        XCTAssertTrue(result.text.contains("Straße"), result.text)
    }

    func testFastLevel() throws {
        let result = try ocr.recognize(fixture("print-en.png"), options: RecognizeOptions(level: .fast))

        XCTAssertTrue(result.text.contains("Invoice"), result.text)
    }

    func testRejectsUnsupportedLanguage() throws {
        XCTAssertThrowsError(
            try ocr.recognize(fixture("print-en.png"), options: RecognizeOptions(languages: ["tlh-QO"]))
        ) { error in
            XCTAssertEqual((error as? OcrError)?.code, "unsupported-language")
        }
    }

    func testSupportedLanguagesIncludeEnglishAndGerman() throws {
        let accurate = try ocr.supportedLanguages(level: .accurate)
        XCTAssertTrue(accurate.contains("en-US"))
        XCTAssertTrue(accurate.contains("de-DE"))
        XCTAssertFalse(try ocr.supportedLanguages(level: .fast).isEmpty)
    }

    func testBlankImageGivesEmptyResult() throws {
        let data = try XCTUnwrap(blankPNG())
        let result = try ocr.recognize(ImageLoader.load(base64: data.base64EncodedString()), options: RecognizeOptions())

        XCTAssertEqual(result.text, "")
        XCTAssertTrue(result.blocks.isEmpty)
        XCTAssertEqual(result.rotation, 0)
        XCTAssertNil(result.language)
    }

    // MARK: - Helpers

    private func fixture(_ name: String) throws -> LoadedImage {
        let url = try XCTUnwrap(Bundle.module.url(forResource: name, withExtension: nil, subdirectory: "Fixtures"))
        return try ImageLoader.load(path: url.path)
    }

    private func assertBox(_ box: Box, left: Double, top: Double, right: Double, bottom: Double,
                           file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(box.x, left, accuracy: tolerance, "left of \(box)", file: file, line: line)
        XCTAssertEqual(box.maxX, right, accuracy: tolerance, "right of \(box)", file: file, line: line)
        XCTAssertEqual(box.y, top, accuracy: tolerance * 2, "top of \(box)", file: file, line: line)
        XCTAssertEqual(box.maxY, bottom, accuracy: tolerance * 2, "bottom of \(box)", file: file, line: line)
    }

    private func blankPNG() -> Data? {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 200, height: 100))
        return renderer.pngData { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 200, height: 100))
        }
    }
}
