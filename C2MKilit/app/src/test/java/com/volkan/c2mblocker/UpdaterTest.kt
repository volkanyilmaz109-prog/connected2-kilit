package com.volkan.c2mblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    @Test
    fun versionParts_readsTags() {
        assertEquals(listOf(3, 2), Updater.versionParts("v3.2"))
        assertEquals(listOf(3, 2, 1), Updater.versionParts("3.2.1"))
        assertEquals(listOf(4, 0), Updater.versionParts("v4.0-beta"))
        assertEquals(emptyList<Int>(), Updater.versionParts("latest"))
    }

    @Test
    fun isNewer_comparesNumerically() {
        assertTrue(Updater.isNewer("v3.10", "3.9"))
        assertTrue(Updater.isNewer("v3.2.1", "3.2"))
        assertTrue(Updater.isNewer("v4.0", "3.2"))
        assertFalse(Updater.isNewer("v3.2", "3.2"))
        assertFalse(Updater.isNewer("v3.2.0", "3.2"))
        assertFalse(Updater.isNewer("v3.1", "3.2"))
        assertFalse(Updater.isNewer("nightly", "3.2"))
    }
}
