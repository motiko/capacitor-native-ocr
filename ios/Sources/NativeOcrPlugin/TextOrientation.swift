import Foundation

/// Which way the text runs in the image. Vision reads sideways and upside-down text, but
/// returns boxes in image coordinates, where a sideways page's lines all share one vertical
/// band. Layout (blocks, table rows) therefore runs in the text's own frame: boxes are turned
/// so the text runs left to right, laid out, and turned back. Quarter turns keep boxes
/// axis-aligned, so the round trip is exact.
enum TextOrientation: Equatable {
    /// Left to right, the normal case.
    case up
    /// Top to bottom: the page is turned a quarter clockwise.
    case runsDown
    /// Right to left and upside down.
    case down
    /// Bottom to top: the page is turned a quarter counter-clockwise.
    case runsUp

    /// The dominant direction among line baselines, each given as a vector in pixels
    /// (top-left origin, y down) and weighted by its length.
    static func dominant(baselines: [(dx: Double, dy: Double)]) -> TextOrientation {
        var votes: [TextOrientation: Double] = [:]
        for baseline in baselines {
            let length = hypot(baseline.dx, baseline.dy)
            guard length > 0 else { continue }
            let direction: TextOrientation
            if abs(baseline.dx) >= abs(baseline.dy) {
                direction = baseline.dx > 0 ? .up : .down
            } else {
                direction = baseline.dy > 0 ? .runsDown : .runsUp
            }
            votes[direction, default: 0] += length
        }
        return votes.max { $0.value < $1.value }?.key ?? .up
    }

    /// From image coordinates into the frame where the text runs left to right.
    func toText(_ box: Box) -> Box {
        transform(box, toText: true)
    }

    /// From the text's frame back to image coordinates.
    func toImage(_ box: Box) -> Box {
        transform(box, toText: false)
    }

    func toText(_ line: OcrLine) -> OcrLine {
        map(line, toText)
    }

    func toImage(_ block: OcrBlock) -> OcrBlock {
        OcrBlock(text: block.text, box: toImage(block.box), lines: block.lines.map { map($0, toImage) })
    }

    private func map(_ line: OcrLine, _ f: (Box) -> Box) -> OcrLine {
        OcrLine(
            text: line.text,
            box: f(line.box),
            confidence: line.confidence,
            words: line.words.map { OcrWord(text: $0.text, box: f($0.box), confidence: $0.confidence) }
        )
    }

    private func point(_ x: Double, _ y: Double, toText: Bool) -> (Double, Double) {
        switch (self, toText) {
        case (.up, _): return (x, y)
        case (.down, _): return (1 - x, 1 - y)
        case (.runsDown, true), (.runsUp, false): return (y, 1 - x)
        case (.runsDown, false), (.runsUp, true): return (1 - y, x)
        }
    }

    private func transform(_ box: Box, toText: Bool) -> Box {
        let a = point(box.x, box.y, toText: toText)
        let b = point(box.maxX, box.maxY, toText: toText)
        return Box(x: min(a.0, b.0), y: min(a.1, b.1), width: abs(a.0 - b.0), height: abs(a.1 - b.1))
    }
}
