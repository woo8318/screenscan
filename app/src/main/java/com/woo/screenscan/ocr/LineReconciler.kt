package com.woo.screenscan.ocr

/**
 * Combines two readings of the same text line. Handles lines that were cut at a frame edge:
 * a fragment inside a longer reading is absorbed, and a left part and a right part that
 * overlap are stitched into one line.
 */
object LineReconciler {
    private const val UPGRADE_MARGIN = 0.05f
    private const val MIN_STITCH_OVERLAP = 6
    private val whitespaceRun = Regex("\\s+")

    /** Returns the line to keep in place of [existing]; indent always comes from [existing]. */
    fun reconcile(existing: TextLine, incoming: TextLine): TextLine {
        val normExisting = LineNormalizer.normalize(existing.body)
        val normIncoming = LineNormalizer.normalize(incoming.body)
        return when {
            normExisting == normIncoming -> moreConfident(existing, incoming)
            normIncoming.contains(normExisting) -> adopt(existing, incoming)
            normExisting.contains(normIncoming) -> existing
            else -> stitch(existing, incoming) ?: moreConfident(existing, incoming)
        }
    }

    private fun moreConfident(existing: TextLine, incoming: TextLine): TextLine =
        if (incoming.confidence > existing.confidence + UPGRADE_MARGIN) adopt(existing, incoming) else existing

    private fun adopt(existing: TextLine, incoming: TextLine): TextLine =
        incoming.copy(
            indent = existing.indent,
            cutLeft = existing.cutLeft && incoming.cutLeft,
            cutRight = existing.cutRight && incoming.cutRight,
        )

    /** Joins a left part and a right part whose ends overlap by at least a few characters. */
    private fun stitch(existing: TextLine, incoming: TextLine): TextLine? {
        val confidence = minOf(existing.confidence, incoming.confidence)
        joinAt(existing.body, incoming.body)?.let { joined ->
            return existing.copy(body = joined, cutRight = incoming.cutRight, confidence = confidence)
        }
        joinAt(incoming.body, existing.body)?.let { joined ->
            return existing.copy(body = joined, cutLeft = incoming.cutLeft, confidence = confidence)
        }
        return null
    }

    /**
     * [left] + [right] where a suffix of left exactly equals a prefix of right (whitespace runs
     * collapsed, up to two garbled characters at the cut edges ignored); null when there is none.
     */
    private fun joinAt(left: String, right: String): String? {
        val l0 = left.replace(whitespaceRun, " ").trimEnd()
        val r0 = right.replace(whitespaceRun, " ").trimStart()
        for (leftDrop in 0..LineNormalizer.EDGE_SLACK) {
            for (rightDrop in 0..LineNormalizer.EDGE_SLACK) {
                val l = l0.dropLast(leftDrop).trimEnd()
                val r = r0.drop(rightDrop).trimStart()
                val longest = minOf(l.length, r.length) - 1
                for (k in longest downTo MIN_STITCH_OVERLAP) {
                    if (l.regionMatches(l.length - k, r, 0, k)) return l + r.drop(k)
                }
            }
        }
        return null
    }
}
