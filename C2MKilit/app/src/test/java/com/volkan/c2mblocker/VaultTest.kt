package com.volkan.c2mblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultTest {

    @Test
    fun generate_isEightLowercaseLettersAndDigits() {
        repeat(500) {
            val p = Vault.generate()
            assertEquals(8, p.length)
            assertTrue(p, p.all { it in 'a'..'z' || it in '2'..'9' })
            assertTrue(p, p.none { it in "lo01" })
        }
    }

    @Test
    fun normalize_dropsSpacesAndDashes() {
        assertEquals("abcd2345", Vault.normalize(" abcd-2345 "))
        assertEquals("Abcd2345", Vault.normalize("Abcd 2345"))
    }
}
