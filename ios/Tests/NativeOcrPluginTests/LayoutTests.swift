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

    func testMergesReceiptPricesIntoRows() {
        // Vision's order: the names column, then the prices column, then the total line.
        let lines = [
            line("3 x Milch", y: 0.30, height: 0.03, width: 0.3),
            line("Apfel lose", y: 0.34, height: 0.03, width: 0.3),
            line("Bananen", y: 0.38, height: 0.03, width: 0.3),
            line("3,27", y: 0.301, height: 0.03, x: 0.7, width: 0.1),
            line("2,87", y: 0.341, height: 0.03, x: 0.7, width: 0.1),
            line("1,76", y: 0.381, height: 0.03, x: 0.7, width: 0.1),
            line("Vielen Dank", y: 0.42, height: 0.03, width: 0.3)
        ]

        let merged = Layout.mergeTableColumns(lines)

        XCTAssertEqual(merged.map(\.text), ["3 x Milch 3,27", "Apfel lose 2,87", "Bananen 1,76", "Vielen Dank"])
        XCTAssertEqual(merged[0].words.map(\.text), ["3", "x", "Milch", "3,27"])
        XCTAssertEqual(merged[0].box.maxX, 0.8, accuracy: 1e-9)
    }

    func testKeepsTwoColumnProseAsColumns() {
        let left = (0..<4).map { line("left column line \($0) with several words", y: 0.3 + Double($0) * 0.04, height: 0.03, width: 0.4) }
        let right = (0..<4).map { line("right column line \($0) with several words", y: 0.3 + Double($0) * 0.04, height: 0.03, x: 0.55, width: 0.4) }

        XCTAssertEqual(Layout.mergeTableColumns(left + right).map(\.text), (left + right).map(\.text))
    }

    func testKeepsSideBySideTextBlocksAsColumns() {
        // A business card: name on the left, address on the right, rows aligned.
        let lines = [
            line("Musterfirma GmbH", y: 0.30, height: 0.05, width: 0.35),
            line("Dokumente Scannen", y: 0.37, height: 0.05, width: 0.35),
            line("Beispielstraße 1", y: 0.30, height: 0.05, x: 0.55, width: 0.35),
            line("00000 Musterstadt", y: 0.37, height: 0.05, x: 0.55, width: 0.35)
        ]

        XCTAssertEqual(Layout.mergeTableColumns(lines).map(\.text), lines.map(\.text))
    }

    func testNumericCells() {
        XCTAssertTrue(Layout.isNumeric("3,27"))
        XCTAssertTrue(Layout.isNumeric("30,00 €"))
        XCTAssertTrue(Layout.isNumeric("2"))
        XCTAssertFalse(Layout.isNumeric("Beispielstraße 1"))
        XCTAssertFalse(Layout.isNumeric("Einzelpreis"))
        XCTAssertFalse(Layout.isNumeric(" "))
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

    private func line(_ text: String, y: Double, height: Double, x: Double = 0.1, width: Double = 0.4) -> OcrLine {
        let box = Box(x: x, y: y, width: width, height: height)
        let words = text.split(separator: " ").map { OcrWord(text: String($0), box: box, confidence: 1) }
        return OcrLine(text: text, box: box, confidence: 1, words: words)
    }
}

final class TextOrientationTests: XCTestCase {
    func testDominantDirectionWeighsLongLines() {
        XCTAssertEqual(TextOrientation.dominant(baselines: [(dx: 100, dy: 2), (dx: -10, dy: 0)]), .up)
        XCTAssertEqual(TextOrientation.dominant(baselines: [(dx: 3, dy: 200)]), .runsDown)
        XCTAssertEqual(TextOrientation.dominant(baselines: [(dx: 0, dy: -150), (dx: 20, dy: 0)]), .runsUp)
        XCTAssertEqual(TextOrientation.dominant(baselines: [(dx: -80, dy: 1)]), .down)
        XCTAssertEqual(TextOrientation.dominant(baselines: []), .up)
    }

    func testRoundTripsEveryOrientationExactly() {
        let box = Box(x: 0.1, y: 0.2, width: 0.3, height: 0.05)
        for orientation in [TextOrientation.up, .runsDown, .down, .runsUp] {
            XCTAssertTrue(orientation.toImage(orientation.toText(box)).isClose(to: box, tolerance: 1e-12), "\(orientation)")
        }
    }

    func testSidewaysLineBecomesHorizontal() {
        // Text running top to bottom near the right edge: a tall, narrow box.
        let box = Box(x: 0.8, y: 0.1, width: 0.05, height: 0.6)
        let turned = TextOrientation.runsDown.toText(box)

        XCTAssertGreaterThan(turned.width, turned.height)
        // The right-hand column is the first line once the page is upright.
        XCTAssertEqual(turned.y, 0.15, accuracy: 1e-9)
    }
}
