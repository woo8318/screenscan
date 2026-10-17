package com.woo.screenscan.ocr

/**
 * Appends a new frame onto accumulated lines by finding where the frame overlaps the tail
 * of what was already captured. Only lines past the overlap are added, and overlapping
 * lines are upgraded when the new frame read them with higher confidence.
 */
object FrameMerger {
    private const val MATCH_THRESHOLD_SHORT = 0.85f
    private const val MATCH_THRESHOLD_LONG = 0.8f
    private const val LONG_LINE_LENGTH = 20
    private const val DUPLICATE_THRESHOLD = 0.75f
    private const val MIN_MATCH_RATIO = 0.5f
    private const val MAX_DUPLICATE_RATIO = 0.5f
    private const val MIN_MATCHES = 2
    private const val MIN_SINGLE_MATCH_LENGTH = 6
    private const val MIN_MATCHED_LENGTH = 6
    private const val SEARCH_WINDOW_FACTOR = 3
    private const val UPGRADE_MARGIN = 0.05f

    data class Alignment(
        /** Index into existing where incoming[0] lines up. */
        val start: Int,
        val overlap: Int,
        val comparable: Int,
        val matchedPairs: List<Pair<Int, Int>>,
        val indentDelta: Int,
    ) {
        val matches: Int get() = matchedPairs.size
    }

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
        if (looksDuplicated(existing, appended, trimmed.size)) {
            return if (upgraded == existing) Result.Unchanged(existing) else Result.Appended(upgraded, 0)
        }
        val merged = upgraded + appended
        return if (merged == existing) Result.Unchanged(existing) else Result.Appended(merged, appended.size)
    }

    /**
     * Picks the offset with the most matching lines first, so a noisy re-read of the same
     * screen (many matches, a few misreads) beats a short tail overlap that happens to be
     * perfect. Only then is the winner checked for quality.
     */
    fun findAlignment(existing: List<TextLine>, incoming: List<TextLine>): Alignment? {
        val window = (incoming.size * SEARCH_WINDOW_FACTOR).coerceAtMost(existing.size)
        val base = existing.size - window
        val normExisting = existing.takeLast(window).map { LineNormalizer.normalize(it.body) }
        val normIncoming = incoming.map { LineNormalizer.normalize(it.body) }

        val best = (0 until window)
            .mapNotNull { offset -> score(existing, incoming, normExisting, normIncoming, offset, base) }
            .maxWithOrNull(compareBy({ it.matches }, { it.overlap }))
            ?: return null
        return best.takeIf { isTrustworthy(it, normIncoming) }
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
        if (matched.isEmpty()) return null
        val deltas = matched.map { (i, j) -> existing[base + j].indent - incoming[i].indent }.sorted()
        val indentDelta = deltas[deltas.size / 2]
        val absolutePairs = matched.map { (i, j) -> i to base + j }
        return Alignment(base + offset, overlap, comparable.size, absolutePairs, indentDelta)
    }

    private fun isTrustworthy(alignment: Alignment, normIncoming: List<String>): Boolean {
        if (alignment.matches.toFloat() / alignment.comparable < MIN_MATCH_RATIO) return false
        return matchesAreStrong(alignment.matchedPairs, normIncoming)
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

    /**
     * Lines about to be appended that already resemble lines in the captured tail are a
     * re-read of the same screen with a mis-chosen anchor, not new content.
     */
    private fun looksDuplicated(existing: List<TextLine>, appended: List<TextLine>, frameSize: Int): Boolean {
        val candidates = appended.filterNot { it.isBlank }
        if (candidates.isEmpty()) return false
        val tail = existing.takeLast(frameSize * SEARCH_WINDOW_FACTOR)
            .filterNot { it.isBlank }
            .map { LineNormalizer.normalize(it.body) }
        val duplicates = candidates.count { line ->
            val norm = LineNormalizer.normalize(line.body)
            norm.length >= MIN_SINGLE_MATCH_LENGTH && tail.any { LineNormalizer.similarity(norm, it) >= DUPLICATE_THRESHOLD }
        }
        return duplicates.toFloat() / candidates.size > MAX_DUPLICATE_RATIO
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
