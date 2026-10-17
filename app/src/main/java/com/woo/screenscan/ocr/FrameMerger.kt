package com.woo.screenscan.ocr

/**
 * Appends a new frame onto accumulated lines by finding where the frame overlaps the tail
 * of what was already captured. Only lines past the overlap are added, and overlapping
 * lines are reconciled with the new reading.
 */
object FrameMerger {
    private const val MATCH_THRESHOLD_SHORT = 0.85f
    private const val MATCH_THRESHOLD_MEDIUM = 0.8f
    private const val MATCH_THRESHOLD_LONG = 0.75f
    private const val MEDIUM_LINE_LENGTH = 10
    private const val LONG_LINE_LENGTH = 20
    private const val DUPLICATE_THRESHOLD = 0.75f
    private const val MIN_MATCH_RATIO = 0.5f
    private const val MIN_MATCHES = 2
    private const val MIN_SINGLE_MATCH_LENGTH = 6
    private const val MIN_MATCHED_LENGTH = 6
    private const val SEARCH_WINDOW_FACTOR = 3
    private const val MIN_FRAGMENT_LENGTH = 8

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

        /** Normalized keys of near-duplicate lines held back until a second frame confirms them. */
        val held: List<String>

        data class Appended(override val lines: List<TextLine>, val added: Int, override val held: List<String> = emptyList()) : Result
        data class NoOverlap(override val lines: List<TextLine>, override val held: List<String> = emptyList()) : Result
        data class Unchanged(override val lines: List<TextLine>, override val held: List<String> = emptyList()) : Result
    }

    /**
     * Merges [incoming] into [existing]. With [requireOverlap] a frame that does not line up
     * with the captured tail is ignored instead of appended, which keeps garbage out while the
     * camera is moving. New lines that merely resemble captured lines are OCR noise unless the
     * same reading was already [confirmed] by the previous frame.
     */
    fun merge(
        existing: List<TextLine>,
        incoming: List<TextLine>,
        requireOverlap: Boolean = false,
        confirmed: Set<String> = emptySet(),
    ): Result {
        val trimmed = trimBlankEdges(incoming)
        if (trimmed.isEmpty()) return Result.Unchanged(existing)
        if (existing.isEmpty()) return Result.Appended(trimmed, trimmed.size)
        val alignment = findAlignment(existing, trimmed)
        if (alignment == null && requireOverlap) return Result.NoOverlap(existing)

        val upgraded = if (alignment == null) existing else upgradeMatched(existing, trimmed, alignment)
        val candidates = if (alignment == null) trimmed else shiftIndent(trimmed.drop(alignment.overlap), alignment.indentDelta)
        val (appended, held) = holdNearDuplicates(existing, candidates, trimmed.size, confirmed)
        val merged = upgraded + appended
        return if (merged == existing) Result.Unchanged(existing, held) else Result.Appended(merged, appended.size, held)
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
        val deltas = matched
            .filterNot { (i, j) -> incoming[i].cutLeft || existing[base + j].cutLeft }
            .map { (i, j) -> existing[base + j].indent - incoming[i].indent }
            .sorted()
        val indentDelta = if (deltas.isEmpty()) 0 else deltas[deltas.size / 2]
        val absolutePairs = matched.map { (i, j) -> i to base + j }
        return Alignment(base + offset, overlap, comparable.size, absolutePairs, indentDelta)
    }

    private fun isTrustworthy(alignment: Alignment, normIncoming: List<String>): Boolean {
        if (alignment.matches.toFloat() / alignment.comparable < MIN_MATCH_RATIO) return false
        return matchesAreStrong(alignment.matchedPairs, normIncoming)
    }

    private fun matchThreshold(length: Int): Float = when {
        length >= LONG_LINE_LENGTH -> MATCH_THRESHOLD_LONG
        length >= MEDIUM_LINE_LENGTH -> MATCH_THRESHOLD_MEDIUM
        else -> MATCH_THRESHOLD_SHORT
    }

    private fun linesMatch(a: String, b: String): Boolean {
        if (LineNormalizer.similarity(a, b) >= matchThreshold(maxOf(a.length, b.length))) return true
        // A fragment cut at the frame edge still identifies the line it came from, either
        // because it sits inside the other reading or because the two readings overlap at the cut.
        if (minOf(a.length, b.length) < MIN_FRAGMENT_LENGTH) return false
        return LineNormalizer.containmentSimilarity(a, b) >= MATCH_THRESHOLD_SHORT ||
            LineNormalizer.edgeOverlap(a, b) >= MIN_FRAGMENT_LENGTH
    }

    /** Generic one-character lines such as braces must not anchor an alignment on their own. */
    private fun matchesAreStrong(matched: List<Pair<Int, Int>>, normIncoming: List<String>): Boolean {
        val matchedLength = matched.sumOf { (i, _) -> normIncoming[i].length }
        if (matchedLength < MIN_MATCHED_LENGTH) return false
        return matched.size >= MIN_MATCHES || normIncoming[matched.single().first].length >= MIN_SINGLE_MATCH_LENGTH
    }

    private fun shiftIndent(lines: List<TextLine>, delta: Int): List<TextLine> = lines.map { line ->
        if (line.isBlank || line.cutLeft) line else line.copy(indent = (line.indent + delta).coerceAtLeast(0))
    }

    /**
     * Splits candidate lines into ones to append now and ones to hold back. A candidate that
     * resembles (but does not equal) a line in the captured tail is most likely a misread of
     * that line; it is appended only when the previous frame produced the same reading.
     * Exact repeats are always kept, since logs legitimately repeat lines.
     */
    private fun holdNearDuplicates(
        existing: List<TextLine>,
        candidates: List<TextLine>,
        frameSize: Int,
        confirmed: Set<String>,
    ): Pair<List<TextLine>, List<String>> {
        val tail = existing.takeLast(frameSize * SEARCH_WINDOW_FACTOR)
            .filterNot { it.isBlank }
            .map { LineNormalizer.normalize(it.body) }
        val held = mutableListOf<String>()
        val kept = candidates.filter { line ->
            val norm = LineNormalizer.normalize(line.body)
            val suspicious = norm.length >= MIN_SINGLE_MATCH_LENGTH && tail.any { known -> isNearDuplicate(norm, known) }
            if (!suspicious || norm in confirmed) true else {
                held += norm
                false
            }
        }
        return kept to held
    }

    private fun isNearDuplicate(candidate: String, known: String): Boolean {
        if (candidate == known) return false
        if (LineNormalizer.similarity(candidate, known) >= DUPLICATE_THRESHOLD) return true
        return candidate.length >= MIN_FRAGMENT_LENGTH &&
            LineNormalizer.containmentSimilarity(candidate, known) >= MATCH_THRESHOLD_SHORT
    }

    /** Improves matched existing lines with the incoming reading (longer, stitched or more confident). */
    private fun upgradeMatched(existing: List<TextLine>, incoming: List<TextLine>, alignment: Alignment): List<TextLine> {
        val better = alignment.matchedPairs
            .associate { (i, j) -> j to LineReconciler.reconcile(existing[j], incoming[i]) }
            .filter { (j, line) -> line != existing[j] }
        if (better.isEmpty()) return existing
        return existing.mapIndexed { index, line -> better[index] ?: line }
    }

    private fun trimBlankEdges(lines: List<TextLine>): List<TextLine> =
        lines.dropWhile { it.isBlank }.dropLastWhile { it.isBlank }
}
