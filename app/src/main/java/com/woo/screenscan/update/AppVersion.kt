package com.woo.screenscan.update

/** Semantic version helpers for comparing the installed build against a release tag. */
object AppVersion {
    private const val DEV_SUFFIX = "-dev"

    fun isDev(version: String): Boolean = version.endsWith(DEV_SUFFIX)

    /** Parses "v1.2.3", "1.2.3" or "1.2.3-dev" into numeric parts; null when not a version. */
    fun parse(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").substringBefore('-')
        if (core.isEmpty()) return null
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        return parts
    }

    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(installed) ?: return false
        val length = maxOf(a.size, b.size)
        for (i in 0 until length) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
