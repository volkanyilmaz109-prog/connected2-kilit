package com.volkan.c2mblocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTextTest {
    @Test
    fun buttonTextsMatch() {
        assertTrue(BlockerService.startsWithWord("Kaldır", "Kaldır"))
        assertTrue(BlockerService.startsWithWord("KALDIR", "Kaldır"))
        assertTrue(BlockerService.startsWithWord("Kaldır (12 MB)", "Kaldır"))
        assertTrue(BlockerService.startsWithWord("  Durmaya zorla ", "Durmaya zorla"))
        assertTrue(BlockerService.startsWithWord("Force stop", "Force stop"))
    }

    @Test
    fun otherTextsDoNotMatch() {
        assertFalse(BlockerService.startsWithWord("Kapalı", "Kapat"))
        assertFalse(BlockerService.startsWithWord("Pil kullanımı", "Kullan"))
        assertFalse(BlockerService.startsWithWord("Stopwatch", "Stop"))
        assertFalse(BlockerService.startsWithWord("Kaldırıldı", "Kaldır"))
        assertFalse(BlockerService.startsWithWord(null, "Kaldır"))
    }
}
