import CoreGraphics
import Foundation

/// Normalized 0..1 rectangle with a top-left origin, as the JavaScript API returns it.
struct Box: Equatable {
    var x: Double
    var y: Double
    var width: Double
    var height: Double

    var maxX: Double { x + width }
    var maxY: Double { y + height }

    /// Converts a Vision rectangle (normalized, bottom-left origin) to a top-left origin.
    init(visionRect rect: CGRect) {
        x = Double(rect.minX)
        y = Double(1 - rect.maxY)
        width = Double(rect.width)
        height = Double(rect.height)
    }

    init(x: Double, y: Double, width: Double, height: Double) {
        self.x = x
        self.y = y
        self.width = width
        self.height = height
    }

    func union(_ other: Box) -> Box {
        let minX = min(x, other.x)
        let minY = min(y, other.y)
        return Box(x: minX, y: minY, width: max(maxX, other.maxX) - minX, height: max(maxY, other.maxY) - minY)
    }

    func isClose(to other: Box, tolerance: Double = 1e-3) -> Bool {
        abs(x - other.x) < tolerance && abs(y - other.y) < tolerance
            && abs(width - other.width) < tolerance && abs(height - other.height) < tolerance
    }
}

struct OcrWord: Equatable {
    var text: String
    var box: Box
    var confidence: Double?
}

struct OcrLine: Equatable {
    var text: String
    var box: Box
    var confidence: Double?
    var words: [OcrWord]
}

struct OcrBlock: Equatable {
    var text: String
    var box: Box
    var lines: [OcrLine]
}

enum Layout {
    /// Ranges of whitespace-separated words. Punctuation stays attached to its word,
    /// as in Tesseract's output, so "Hello," is one word.
    static func wordRanges(in text: String) -> [Range<String.Index>] {
        var ranges: [Range<String.Index>] = []
        var start: String.Index?
        var index = text.startIndex
        while index < text.endIndex {
            if text[index].isWhitespace {
                if let wordStart = start {
                    ranges.append(wordStart..<index)
                    start = nil
                }
            } else if start == nil {
                start = index
            }
            index = text.index(after: index)
        }
        if let wordStart = start {
            ranges.append(wordStart..<text.endIndex)
        }
        return ranges
    }

    /// Splits a line's box between its words in proportion to their character offsets.
    /// Used when the engine can't give a box for a word on its own.
    static func estimatedWordBox(for range: Range<String.Index>, in text: String, lineBox: Box) -> Box {
        let total = Double(max(text.count, 1))
        let start = Double(text.distance(from: text.startIndex, to: range.lowerBound))
        let length = Double(text.distance(from: range.lowerBound, to: range.upperBound))
        return Box(
            x: lineBox.x + lineBox.width * start / total,
            y: lineBox.y,
            width: lineBox.width * length / total,
            height: lineBox.height
        )
    }

    /// Groups lines that are already in reading order into blocks: a line joins the
    /// previous block when it sits just below the block's last line, overlaps it
    /// horizontally and has a similar text height.
    static func groupIntoBlocks(_ lines: [OcrLine]) -> [OcrBlock] {
        var groups: [[OcrLine]] = []
        for line in lines {
            if let previous = groups.last?.last, continues(previous, with: line) {
                groups[groups.count - 1].append(line)
            } else {
                groups.append([line])
            }
        }
        return groups.map { group in
            let box = group.dropFirst().reduce(group[0].box) { $0.union($1.box) }
            return OcrBlock(text: group.map(\.text).joined(separator: "\n"), box: box, lines: group)
        }
    }

    /// Puts table cells back into rows. Vision returns a table column by column, so a receipt
    /// reads as all item names, then all prices. A column to the right of another is merged into
    /// it row by row when its lines are short (median ≤ 3 words), mostly numbers (prices,
    /// amounts, quantities), and at least 60 % of them sit on the same row as a line on the left.
    /// Two-column prose and side-by-side text blocks, such as a business card, stay as columns.
    static func mergeTableColumns(_ lines: [OcrLine]) -> [OcrLine] {
        var columns = groupIntoBlocks(lines).map(\.lines)
        while let merge = nextTableMerge(columns) {
            for (cell, partner) in merge.partners {
                columns[partner.column][partner.line] = joined(columns[partner.column][partner.line], columns[merge.column][cell])
            }
            // Cells without a row stay a column of their own, so a later pass can pair them.
            let joinedCells = Set(merge.partners.map(\.cell))
            columns[merge.column] = columns[merge.column].enumerated().filter { !joinedCells.contains($0.offset) }.map(\.element)
            columns.removeAll(where: \.isEmpty)
        }
        return columns.flatMap { $0 }
    }

    private struct TableMerge {
        let column: Int
        /// Each joined cell's index in `column`, with the line it joins.
        let partners: [(cell: Int, partner: (column: Int, line: Int))]
    }

    /// A column of table cells and, for each cell, the line on its row to its left in any
    /// other column (the nearest one). The left side of a table often splits into several
    /// blocks, so partners aren't limited to one column.
    private static func nextTableMerge(_ columns: [[OcrLine]]) -> TableMerge? {
        for (index, cells) in columns.enumerated() {
            let wordCounts = cells.map(\.words.count).sorted()
            guard wordCounts[wordCounts.count / 2] <= 3,
                  Double(cells.filter { isNumeric($0.text) }.count) >= 0.6 * Double(cells.count)
            else { continue }

            var partners: [(cell: Int, partner: (column: Int, line: Int))] = []
            for (cellIndex, cell) in cells.enumerated() {
                var best: (column: Int, line: Int, maxX: Double)?
                for (other, lines) in columns.enumerated() where other != index {
                    for (lineIndex, line) in lines.enumerated()
                    where sameRow(line.box, cell.box) && line.box.maxX < cell.box.x && line.box.maxX > (best?.maxX ?? -1) {
                        best = (other, lineIndex, line.box.maxX)
                    }
                }
                if let best {
                    partners.append((cellIndex, (best.column, best.line)))
                }
            }
            if !partners.isEmpty, Double(partners.count) >= 0.6 * Double(cells.count) {
                return TableMerge(column: index, partners: partners)
            }
        }
        return nil
    }

    /// At least half of the non-space characters are digits; currency signs, separators and
    /// units don't count against it ("12,50 €", "2", "3 × 1,29").
    static func isNumeric(_ text: String) -> Bool {
        let characters = text.filter { !$0.isWhitespace }
        guard !characters.isEmpty else { return false }
        return Double(characters.filter(\.isNumber).count) >= 0.5 * Double(characters.count)
    }

    private static func sameRow(_ a: Box, _ b: Box) -> Bool {
        let overlap = min(a.maxY, b.maxY) - max(a.y, b.y)
        return overlap >= 0.5 * min(a.height, b.height)
    }

    private static func joined(_ a: OcrLine, _ b: OcrLine) -> OcrLine {
        let confidence = [a.confidence, b.confidence].compactMap { $0 }.min()
        return OcrLine(text: a.text + " " + b.text, box: a.box.union(b.box), confidence: confidence, words: a.words + b.words)
    }

    /// Lines joined by `\n`, blocks separated by an empty line.
    static func text(of blocks: [OcrBlock]) -> String {
        blocks.map(\.text).joined(separator: "\n\n")
    }

    private static func continues(_ previous: OcrLine, with line: OcrLine) -> Bool {
        let a = previous.box
        let b = line.box
        let lineHeight = min(a.height, b.height)
        guard lineHeight > 0 else { return false }

        let heightRatio = b.height / a.height
        let gap = b.y - a.maxY
        let overlapsHorizontally = min(a.maxX, b.maxX) > max(a.x, b.x)

        return overlapsHorizontally
            && (0.6...1.6).contains(heightRatio)
            && gap > -0.5 * lineHeight
            && gap < 0.9 * lineHeight
    }
}
