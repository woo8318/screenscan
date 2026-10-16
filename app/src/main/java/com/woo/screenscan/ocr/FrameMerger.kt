package com.woo.screenscan.ocr

/**
 * Appends a new frame onto accumulated lines by finding where the frame overlaps the tail
 * of what was already captured. Only lines past the overlap are added.
 */
object FrameMerger {
    private const val MATCH_THRESHOLD = 0.9f
    private const val MIN_MATCH_RATIO = 0.6f
    private const val MIN_MATCHES = 2
    private const val MIN_SINGLE_MATCH_LENGTH = 6
    private const val MIN_MATCHED_LENGTH = 6
    private const val SEARCH_WINDOW_FACTOR = 3

    data class Alignment(
        /** Index into existing where incoming[0] lines up. */
        val start: Int,
        val overlap: Int,
        val matches: Int,
        val indentDelta: Int,
    )

    fun merge(existing: List<TextLine>, incoming: List<TextLine>): List<TextLine> {
        val trimmed = trimBlankEdges(incoming)
        if (trimmed.isEmpty()) return existing
        if (existing.isEmpty()) return trimmed
        val alignment = findAlignment(existing, trimmed) ?: return existing + trimmed
        val appended = trimmed.drop(alignment.overlap).map { line ->
            if (line.isBlank) line else line.copy(indent = (line.indent + alignment.indentDelta).coerceAtLeast(0))
        }
        return existing + appended
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
        val matched = comparable.filter { (i, j) ->
            LineNormalizer.similarity(normIncoming[i], normExisting[j]) >= MATCH_THRESHOLD
        }
        val matches = matched.size
        if (matches.toFloat() / comparable.size < MIN_MATCH_RATIO) return null
        if (!matchesAreStrong(matched, normIncoming)) return null
        val deltas = matched.map { (i, j) -> existing[base + j].indent - incoming[i].indent }.sorted()
        val indentDelta = if (deltas.isEmpty()) 0 else deltas[deltas.size / 2]
        return Alignment(base + offset, overlap, matches, indentDelta)
    }

    /** Generic one-character lines such as braces must not anchor an alignment on their own. */
    private fun matchesAreStrong(matched: List<Pair<Int, Int>>, normIncoming: List<String>): Boolean {
        val matchedLength = matched.sumOf { (i, _) -> normIncoming[i].length }
        if (matchedLength < MIN_MATCHED_LENGTH) return false
        return matched.size >= MIN_MATCHES || normIncoming[matched.single().first].length >= MIN_SINGLE_MATCH_LENGTH
    }

    private fun trimBlankEdges(lines: List<TextLine>): List<TextLine> =
        lines.dropWhile { it.isBlank }.dropLastWhile { it.isBlank }
}
