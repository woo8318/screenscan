package com.woo.screenscan.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun parsesTagAndPlainVersions() {
        assertEquals(listOf(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(listOf(0, 1, 0), AppVersion.parse("0.1.0"))
        assertEquals(listOf(0, 0, 0), AppVersion.parse("0.0.0-dev"))
        assertNull(AppVersion.parse("latest"))
    }

    @Test
    fun comparesNumericallyNotLexically() {
        assertTrue(AppVersion.isNewer("v0.10.0", "0.9.0"))
        assertTrue(AppVersion.isNewer("v1.0", "0.9.9"))
        assertFalse(AppVersion.isNewer("v0.1.0", "0.1.0"))
        assertFalse(AppVersion.isNewer("v0.1.0", "0.2.0"))
    }

    @Test
    fun unparsableVersionsNeverTriggerAnUpdate() {
        assertFalse(AppVersion.isNewer("nightly", "0.1.0"))
        assertFalse(AppVersion.isNewer("v0.2.0", "unknown"))
    }

    @Test
    fun detectsDevBuilds() {
        assertTrue(AppVersion.isDev("0.0.0-dev"))
        assertFalse(AppVersion.isDev("0.2.0"))
    }
}
