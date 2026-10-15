package com.ketangpai.checkin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLocationTest {
    private val now = 300_000_000_000L
    private fun fix(ageSeconds: Long, accuracy: Float? = 20f, provider: String = "gps") =
        LocationFix(30.0, 120.0, accuracy, provider, now - ageSeconds * 1_000_000_000)

    @Test fun staleGpsCannotMaskFreshNetworkLocation() {
        val network = fix(1, 150f, "network")
        assertEquals(network, bestLocation(listOf(fix(61, 3f), network), now))
    }

    @Test fun freshPreciseFixWinsOverMoreAccurateOldCache() {
        val network = fix(1, 60f, "network")
        assertEquals(network, bestLocation(listOf(fix(50, 3f), network), now))
    }

    @Test fun coarseLocationDoesNotQualifyForImmediateCacheReturn() {
        assertFalse(fix(1, 800f).precise(now))
        assertFalse(fix(31, 10f).precise(now))
        assertTrue(fix(30, 100f).precise(now))
    }

    @Test fun invalidAndFutureFixesAreNotReturned() {
        assertNull(bestLocation(listOf(fix(-1), fix(61), fix(0).copy(latitude = Double.NaN), fix(0).copy(longitude = 181.0)), now))
    }

    @Test fun missingOrInvalidAccuracyCannotBeatKnownPrecision() {
        val known = fix(2, 80f)
        assertEquals(known, bestLocation(listOf(fix(0, null), fix(0, Float.NaN), fix(0, -1f), known), now))
        assertFalse(fix(0, null).precise(now))
    }

    @Test fun equallyAccurateLocationsPreferNewestReading() {
        val newest = fix(0)
        assertEquals(newest, bestLocation(listOf(fix(15), newest), now))
    }
}
