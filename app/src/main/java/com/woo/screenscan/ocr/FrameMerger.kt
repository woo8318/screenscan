package com.woo.screenscan.ocr

/**
 * Appends a new frame onto accumulated lines by finding where the frame overlaps the tail
 * of what was already captured. Only lines past the overlap are added, and overlapping
 * lines are upgraded when the new frame read them with higher confidence.
 */
object FrameMerger {
    private const val MATCH_THRESHOLD_SHORT = 0.9f
    private const val MATCH_THRESHOLD_LONG = 0.85f
    private const val LONG_LINE_LENGTH = 20
    private const val MIN_MATCH_RATIO = 0.6f
    private const val MIN_MATCHES = 2
    private const val MIN_SINGLE_MATCH_LENGTH = 6
    private const val MIN_MATCHED_LENGTH = 6
    private const val SEARCH_WINDOW_FACTOR = 3
    private const val UPGRADE_MARGIN = 0.05f

    data class Alignment(
        /** Index into existing where incoming[0] lines up. */
        val start: Int,
        val overlap: Int,
        val matches: Int,
        val indentDelta: Int,
        val matchedPairs: List<Pair<Int, Int>>,
    )

    sealed interface Result {
        val lines: List<TextLine>

        data class Appended(override val lines: List<TextLine>, val added: Int) : Result
        data class NoOverlap(override val lines: List<TextLine>) : Result
        data class Unchanged(override val lines: List<TextLine>) : Result
    }

    /**
     * Merges [incoming] into [existing]. With [requireOverlap] a frame that does not line up
     * with the captured tail is ignored instead of appended, which keeps garbage out while the
     * camera is moving.
     */
    fun merge(existing: List<TextLine>, incoming: List<TextLine>, requireOverlap: Boolean = false): Result {
        val trimmed = trimBlankEdges(incoming)
        if (trimmed.isEmpty()) return Result.Unchanged(existing)
        if (existing.isEmpty()) return Result.Appended(trimmed, trimmed.size)
        val alignment = findAlignment(existing, trimmed)
            ?: return if (requireOverlap) Result.NoOverlap(existing) else Result.Appended(existing + trimmed, trimmed.size)

        val upgraded = upgradeMatched(existing, trimmed, alignment)
        val appended = trimmed.drop(alignment.overlap).map { line ->
            if (line.isBlank) line else line.copy(indent = (line.indent + alignment.indentDelta).coerceAtLeast(0))
        }
        val merged = upgraded + appended
        return if (merged == existing) Result.Unchanged(existing) else Result.Appended(merged, appended.size)
    }

    fun findAlignment(existing: List<TextLine>, incoming: List<TextLine>): Alignment? {
        val window = (incoming.size * SEARCH_WINDOW_FACTOR).coerceAtMost(existing.size)
        val base = existing.size - window
        val normExisting = existing.takeLast(window).map { LineNormalizer.normalize(it.body) }
        val normIncoming = incoming.map { LineNormalizer.normalize(it.body) }

        return (0 until window)
            .mapNotNull { offset -> score(existing, incoming, normExisting, normIncoming, offset, base) }
            .maxWithOrNull(compareBy({ it.matches }, { it.matches.toFloat() / it.overlap }))
    }

    private fun score(
        existing: List<TextLine>,
        incoming: List<TextLine>,
        normExisting: List<String>,
        normIncoming: List<String>,
        offset: Int,
        base: Int,
    ): Alignment? {
        val overlap = minOf(normExisting.size - offset, normIncoming.size)
        val pairs = (0 until overlap).map { i -> i to offset + i }
        val comparable = pairs.filterNot { (i, j) -> normIncoming[i].isEmpty() && normExisting[j].isEmpty() }
        if (comparable.isEmpty()) return null
        val matched = comparable.filter { (i, j) -> linesMatch(normIncoming[i], normExisting[j]) }
        val matches = matched.size
        if (matches.toFloat() / comparable.size < MIN_MATCH_RATIO) return null
        if (!matchesAreStrong(matched, normIncoming)) return null
        val deltas = matched.map { (i, j) -> existing[base + j].indent - incoming[i].indent }.sorted()
        val indentDelta = if (deltas.isEmpty()) 0 else deltas[deltas.size / 2]
        val absolutePairs = matched.map { (i, j) -> i to base + j }
        return Alignment(base + offset, overlap, matches, indentDelta, absolutePairs)
    }

    private fun linesMatch(a: String, b: String): Boolean {
        val threshold = if (maxOf(a.length, b.length) >= LONG_LINE_LENGTH) MATCH_THRESHOLD_LONG else MATCH_THRESHOLD_SHORT
        return LineNormalizer.similarity(a, b) >= threshold
    }

    /** Generic one-character lines such as braces must not anchor an alignment on their own. */
    private fun matchesAreStrong(matched: List<Pair<Int, Int>>, normIncoming: List<String>): Boolean {
        val matchedLength = matched.sumOf { (i, _) -> normIncoming[i].length }
        if (matchedLength < MIN_MATCHED_LENGTH) return false
        return matched.size >= MIN_MATCHES || normIncoming[matched.single().first].length >= MIN_SINGLE_MATCH_LENGTH
    }

    /** Replaces matched existing lines with the incoming reading when it is clearly more confident. */
    private fun upgradeMatched(existing: List<TextLine>, incoming: List<TextLine>, alignment: Alignment): List<TextLine> {
        val better = alignment.matchedPairs
            .filter { (i, j) -> incoming[i].confidence > existing[j].confidence + UPGRADE_MARGIN }
            .associate { (i, j) -> j to incoming[i].copy(indent = existing[j].indent) }
        if (better.isEmpty()) return existing
        return existing.mapIndexed { index, line -> better[index] ?: line }
    }

    private fun trimBlankEdges(lines: List<TextLine>): List<TextLine> =
        lines.dropWhile { it.isBlank }.dropLastWhile { it.isBlank }
}
