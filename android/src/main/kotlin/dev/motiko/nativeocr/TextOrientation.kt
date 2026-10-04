package dev.motiko.nativeocr

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Which way the text runs in the image. ML Kit reads sideways and upside-down text, but
 * returns boxes in image coordinates, where a sideways page's lines all share one vertical
 * band. Layout (blocks, table rows) therefore runs in the text's own frame: boxes are turned
 * so the text runs left to right, laid out, and turned back. Quarter turns keep boxes
 * axis-aligned, so the round trip is exact.
 */
enum class TextOrientation(
    /** Clockwise degrees that turn the image upright (`RecognizeResult.rotation`). */
    val rotation: Int,
) {
    /** Left to right, the normal case. */
    UP(0),

    /** Top to bottom: the page is turned a quarter clockwise. */
    RUNS_DOWN(270),

    /** Right to left and upside down. */
    DOWN(180),

    /** Bottom to top: the page is turned a quarter counter-clockwise. */
    RUNS_UP(90);

    val isSideways: Boolean get() = this == RUNS_DOWN || this == RUNS_UP

    /** From image coordinates into the frame where the text runs left to right. */
    fun toText(box: Box): Box = transform(box, toText = true)

    /** From the text's frame back to image coordinates. */
    fun toImage(box: Box): Box = transform(box, toText = false)

    fun toText(line: OcrLine): OcrLine = map(line, ::toText)

    fun toImage(block: OcrBlock): OcrBlock =
        OcrBlock(block.text, toImage(block.box), block.lines.map { map(it, ::toImage) })

    private fun map(line: OcrLine, f: (Box) -> Box): OcrLine =
        line.copy(box = f(line.box), words = line.words.map { it.copy(box = f(it.box)) })

    private fun point(x: Double, y: Double, toText: Boolean): Pair<Double, Double> = when {
        this == UP -> x to y
        this == DOWN -> (1 - x) to (1 - y)
        (this == RUNS_DOWN) == toText -> y to (1 - x)
        else -> (1 - y) to x
    }

    private fun transform(box: Box, toText: Boolean): Box {
        val a = point(box.x, box.y, toText)
        val b = point(box.maxX, box.maxY, toText)
        return Box(min(a.first, b.first), min(a.second, b.second), abs(a.first - b.first), abs(a.second - b.second))
    }

    companion object {
        /**
         * The dominant direction among line baselines, each given as a vector in pixels
         * (top-left origin, y down) and weighted by its length.
         */
        fun dominant(baselines: List<Pair<Double, Double>>): TextOrientation {
            val votes = mutableMapOf<TextOrientation, Double>()
            for ((dx, dy) in baselines) {
                val length = hypot(dx, dy)
                if (length <= 0) continue
                val direction = if (abs(dx) >= abs(dy)) {
                    if (dx > 0) UP else DOWN
                } else {
                    if (dy > 0) RUNS_DOWN else RUNS_UP
                }
                votes[direction] = (votes[direction] ?: 0.0) + length
            }
            return votes.maxByOrNull { it.value }?.key ?: UP
        }
    }
}
