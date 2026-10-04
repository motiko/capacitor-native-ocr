import XCTest
@testable import NativeOcrPlugin

final class LayoutTests: XCTestCase {
    func testVisionRectFlipsToTopLeftOrigin() {
        let box = Box(visionRect: CGRect(x: 0.1, y: 0.7, width: 0.5, height: 0.2))

        XCTAssertEqual(box.x, 0.1, accuracy: 1e-9)
        XCTAssertEqual(box.y, 0.1, accuracy: 1e-9)
        XCTAssertEqual(box.width, 0.5, accuracy: 1e-9)
        XCTAssertEqual(box.height, 0.2, accuracy: 1e-9)
    }

    func testWordRangesKeepPunctuationAndSkipRepeatedSpaces() {
        let text = "  Total:  1,234.56 EUR "
        XCTAssertEqual(Layout.wordRanges(in: text).map { String(text[$0]) }, ["Total:", "1,234.56", "EUR"])
        XCTAssertTrue(Layout.wordRanges(in: "   ").isEmpty)
        XCTAssertTrue(Layout.wordRanges(in: "").isEmpty)
    }

    func testEstimatedWordBoxSplitsLineByCharacters() {
        let text = "ab cdef"
        let ranges = Layout.wordRanges(in: text)
        let lineBox = Box(x: 0.1, y: 0.2, width: 0.7, height: 0.05)

        let second = Layout.estimatedWordBox(for: ranges[1], in: text, lineBox: lineBox)

        XCTAssertEqual(second.x, 0.4, accuracy: 1e-9)
        XCTAssertEqual(second.width, 0.4, accuracy: 1e-9)
        XCTAssertEqual(second.y, 0.2)
        XCTAssertEqual(second.height, 0.05)
    }

    func testGroupsCloseLinesAndSplitsOnGapsAndHeadings() {
        let lines = [
            line("Heading", y: 0.10, height: 0.08),
            line("first", y: 0.30, height: 0.04),
            line("second", y: 0.35, height: 0.04),
            line("after gap", y: 0.60, height: 0.04),
            line("right column", y: 0.64, height: 0.04, x: 0.6)
        ]

        let blocks = Layout.groupIntoBlocks(lines)

        XCTAssertEqual(blocks.map(\.text), ["Heading", "first\nsecond", "after gap", "right column"])
        XCTAssertTrue(blocks[1].box.isClose(to: Box(x: 0.1, y: 0.30, width: 0.4, height: 0.09), tolerance: 1e-9))
        XCTAssertEqual(Layout.text(of: blocks), "Heading\n\nfirst\nsecond\n\nafter gap\n\nright column")
    }

    func testResolvesLanguagesToVisionIdentifiers() throws {
        let supported = ["en-US", "fr-FR", "de-DE", "zh-Hans", "zh-Hant"]

        XCTAssertEqual(try NativeOcr.resolveLanguages(["de-DE", "en-US"], supported: supported), ["de-DE", "en-US"])
        XCTAssertEqual(try NativeOcr.resolveLanguages(["de", "en_gb", "DE-at"], supported: supported), ["de-DE", "en-US"])
        XCTAssertEqual(try NativeOcr.resolveLanguages(["zh-Hant"], supported: supported), ["zh-Hant"])
        XCTAssertThrowsError(try NativeOcr.resolveLanguages(["he-IL"], supported: supported)) { error in
            XCTAssertEqual((error as? OcrError)?.code, "unsupported-language")
        }
    }

    private func line(_ text: String, y: Double, height: Double, x: Double = 0.1) -> OcrLine {
        let box = Box(x: x, y: y, width: 0.4, height: height)
        return OcrLine(text: text, box: box, confidence: 1, words: [OcrWord(text: text, box: box, confidence: 1)])
    }
}
