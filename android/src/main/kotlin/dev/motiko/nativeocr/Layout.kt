package dev.motiko.nativeocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Normalized 0..1 rectangle with a top-left origin, as the JavaScript API returns it. */
data class Box(val x: Double, val y: Double, val width: Double, val height: Double) {
    val maxX: Double get() = x + width
    val maxY: Double get() = y + height

    fun union(other: Box): Box {
        val minX = min(x, other.x)
        val minY = min(y, other.y)
        return Box(minX, minY, max(maxX, other.maxX) - minX, max(maxY, other.maxY) - minY)
    }

    fun isClose(other: Box, tolerance: Double = 1e-3): Boolean =
        abs(x - other.x) < tolerance && abs(y - other.y) < tolerance &&
            abs(width - other.width) < tolerance && abs(height - other.height) < tolerance

    companion object {
        /** A pixel rectangle as a fraction of the image. */
        fun fromPixels(left: Double, top: Double, right: Double, bottom: Double, imageWidth: Int, imageHeight: Int): Box =
            Box(left / imageWidth, top / imageHeight, (right - left) / imageWidth, (bottom - top) / imageHeight)
    }
}

data class OcrWord(val text: String, val box: Box, val confidence: Double?)

data class OcrLine(val text: String, val box: Box, val confidence: Double?, val words: List<OcrWord>)

data class OcrBlock(val text: String, val box: Box, val lines: List<OcrLine>)

object Layout {
    /**
     * Ranges of whitespace-separated words. Punctuation stays attached to its word,
     * as in Tesseract's output, so "Hello," is one word.
     */
    fun wordRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = -1
        for (index in text.indices) {
            if (text[index].isWhitespace()) {
                if (start >= 0) {
                    ranges.add(start until index)
                    start = -1
                }
            } else if (start < 0) {
                start = index
            }
        }
        if (start >= 0) {
            ranges.add(start until text.length)
        }
        return ranges
    }

    /**
     * Splits a line's box between its words in proportion to their character offsets.
     * Used when the engine can't give a box for a word on its own.
     */
    fun estimatedWordBox(range: IntRange, text: String, lineBox: Box): Box {
        val total = max(text.length, 1).toDouble()
        val start = range.first.toDouble()
        val length = (range.last + 1 - range.first).toDouble()
        return Box(
            x = lineBox.x + lineBox.width * start / total,
            y = lineBox.y,
            width = lineBox.width * length / total,
            height = lineBox.height,
        )
    }

    /**
     * Joins pieces of one line. ML Kit sometimes splits a line into fragments a word apart,
     * mostly on sideways pages ("The quick" | "brown fox"). Pieces on the same row, with a
     * similar height and a gap under one line height, become one line, left to right.
     * `aspect` is the frame's width over its height, so gaps and heights compare in pixels.
     */
    fun joinFragments(lines: List<OcrLine>, aspect: Double): List<OcrLine> {
        val joinedLines = mutableListOf<OcrLine>()
        for (line in lines.sortedBy { it.box.x }) {
            val index = joinedLines.indexOfLast { isFragmentBefore(it, line, aspect) }
            if (index >= 0) {
                joinedLines[index] = joined(joinedLines[index], line)
            } else {
                joinedLines.add(line)
            }
        }
        return joinedLines
    }

    private fun isFragmentBefore(a: OcrLine, b: OcrLine, aspect: Double): Boolean {
        val height = min(a.box.height, b.box.height)
        if (height <= 0) return false
        val gap = (b.box.x - a.box.maxX) * aspect
        return sameRow(a.box, b.box) &&
            b.box.height / a.box.height in 0.6..1.6 &&
            gap > -0.5 * height &&
            gap < height
    }

    /**
     * Puts lines into reading order, column by column as Apple Vision returns them: lines are
     * chained into columns top to bottom (a line continues the column whose last line it sits
     * just below, as in [groupIntoBlocks]), and columns are read in the order they start,
     * left to right when they start on the same row.
     */
    fun readingOrder(lines: List<OcrLine>): List<OcrLine> {
        val chains = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.sortedBy { it.box.y }) {
            val chain = chains.lastOrNull { continues(it.last(), line) }
            if (chain != null) {
                chain.add(line)
            } else {
                chains.add(mutableListOf(line))
            }
        }
        // Chains are already sorted by their first line's top; sort each row of starts by x.
        val ordered = mutableListOf<List<OcrLine>>()
        var rowStart = 0
        while (rowStart < chains.size) {
            val first = chains[rowStart].first().box
            var rowEnd = rowStart + 1
            while (rowEnd < chains.size && sameRow(first, chains[rowEnd].first().box)) rowEnd++
            ordered += chains.subList(rowStart, rowEnd).sortedBy { it.first().box.x }
            rowStart = rowEnd
        }
        return ordered.flatten()
    }

    /**
     * Groups lines that are already in reading order into blocks: a line joins the
     * previous block when it sits just below the block's last line, overlaps it
     * horizontally and has a similar text height.
     */
    fun groupIntoBlocks(lines: List<OcrLine>): List<OcrBlock> {
        val groups = mutableListOf<MutableList<OcrLine>>()
        for (line in lines) {
            val previous = groups.lastOrNull()?.last()
            if (previous != null && continues(previous, line)) {
                groups.last().add(line)
            } else {
                groups.add(mutableListOf(line))
            }
        }
        return groups.map { group ->
            val box = group.drop(1).fold(group[0].box) { box, line -> box.union(line.box) }
            OcrBlock(group.joinToString("\n") { it.text }, box, group)
        }
    }

    /**
     * Puts table cells back into rows. Engines return a table column by column, so a receipt
     * reads as all item names, then all prices. A column to the right of another is merged into
     * it row by row when its lines are short (median ≤ 3 words), mostly numbers (prices,
     * amounts, quantities), and at least 60 % of them sit on the same row as a line on the left.
     * Two-column prose and side-by-side text blocks, such as a business card, stay as columns.
     */
    fun mergeTableColumns(lines: List<OcrLine>): List<OcrLine> {
        val columns = groupIntoBlocks(lines).map { it.lines.toMutableList() }.toMutableList()
        while (true) {
            val merge = nextTableMerge(columns) ?: break
            for ((cell, partner) in merge.partners) {
                val (column, line) = partner
                columns[column][line] = joined(columns[column][line], columns[merge.column][cell])
            }
            // Cells without a row stay a column of their own, so a later pass can pair them.
            val joinedCells = merge.partners.map { it.first }.toSet()
            columns[merge.column] = columns[merge.column].filterIndexed { index, _ -> index !in joinedCells }.toMutableList()
            columns.removeAll { it.isEmpty() }
        }
        return columns.flatten()
    }

    /** A column of table cells, and for each joined cell its index with the line it joins. */
    private class TableMerge(val column: Int, val partners: List<Pair<Int, Pair<Int, Int>>>)

    /**
     * A column of table cells and, for each cell, the line on its row to its left in any
     * other column (the nearest one). The left side of a table often splits into several
     * blocks, so partners aren't limited to one column.
     */
    private fun nextTableMerge(columns: List<List<OcrLine>>): TableMerge? {
        for ((index, cells) in columns.withIndex()) {
            val wordCounts = cells.map { it.words.size }.sorted()
            if (wordCounts[wordCounts.size / 2] > 3 || cells.count { isNumeric(it.text) } < 0.6 * cells.size) continue

            val partners = mutableListOf<Pair<Int, Pair<Int, Int>>>()
            for ((cellIndex, cell) in cells.withIndex()) {
                var best: Triple<Int, Int, Double>? = null
                for ((other, otherLines) in columns.withIndex()) {
                    if (other == index) continue
                    for ((lineIndex, line) in otherLines.withIndex()) {
                        if (sameRow(line.box, cell.box) && line.box.maxX < cell.box.x &&
                            line.box.maxX > (best?.third ?: -1.0)
                        ) {
                            best = Triple(other, lineIndex, line.box.maxX)
                        }
                    }
                }
                best?.let { partners.add(cellIndex to (it.first to it.second)) }
            }
            if (partners.isNotEmpty() && partners.size >= 0.6 * cells.size) {
                return TableMerge(index, partners)
            }
        }
        return null
    }

    /**
     * At least half of the non-space characters are digits; currency signs, separators and
     * units don't count against it ("12,50 €", "2", "3 × 1,29").
     */
    fun isNumeric(text: String): Boolean {
        val characters = text.filterNot { it.isWhitespace() }
        if (characters.isEmpty()) return false
        return characters.count { it.isDigit() } >= 0.5 * characters.length
    }

    private fun sameRow(a: Box, b: Box): Boolean {
        val overlap = min(a.maxY, b.maxY) - max(a.y, b.y)
        return overlap >= 0.5 * min(a.height, b.height)
    }

    private fun joined(a: OcrLine, b: OcrLine): OcrLine {
        val confidence = listOfNotNull(a.confidence, b.confidence).minOrNull()
        return OcrLine(a.text + " " + b.text, a.box.union(b.box), confidence, a.words + b.words)
    }

    /** Lines joined by `\n`, blocks separated by an empty line. */
    fun text(blocks: List<OcrBlock>): String = blocks.joinToString("\n\n") { it.text }

    private fun continues(previous: OcrLine, line: OcrLine): Boolean {
        val a = previous.box
        val b = line.box
        val lineHeight = min(a.height, b.height)
        if (lineHeight <= 0) return false

        val heightRatio = b.height / a.height
        val gap = b.y - a.maxY
        val overlapsHorizontally = min(a.maxX, b.maxX) > max(a.x, b.x)

        return overlapsHorizontally &&
            heightRatio in 0.6..1.6 &&
            gap > -0.5 * lineHeight &&
            gap < 0.9 * lineHeight
    }
}
