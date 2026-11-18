package com.volkan.c2mblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteBlocklistTest {

    @Test
    fun hostOf_readsAddressBarText() {
        assertEquals("example.com", SiteBlocklist.hostOf("example.com"))
        assertEquals("example.com", SiteBlocklist.hostOf("https://www.example.com/a/b?q=1#x"))
        assertEquals("m.example.com", SiteBlocklist.hostOf("m.example.com/path"))
        assertEquals("example.com", SiteBlocklist.hostOf("HTTP://Example.COM:8080/"))
        assertEquals("example.com", SiteBlocklist.hostOf("user@example.com"))
        assertEquals("google.com", SiteBlocklist.hostOf("google.com/search?q=hello world".substringBefore(' ')))
    }

    @Test
    fun hostOf_ignoresInvisibleCharacters() {
        assertEquals("example.com", SiteBlocklist.hostOf("\u200Eexample.com\u200F"))
        assertEquals("example.com", SiteBlocklist.hostOf("\u2068https://example.com/\u2069"))
        assertEquals("example.com", SiteBlocklist.hostOf("\u00A0example.com\u00A0"))
    }

    @Test
    fun keywordEntriesMatchAnywhereInHost() {
        val set = setOf("instagram", "example.org")
        assertEquals("instagram", SiteBlocklist.matchIn(set, "instagram.com"))
        assertEquals("instagram", SiteBlocklist.matchIn(set, "m.instagram.com"))
        assertEquals("instagram", SiteBlocklist.matchIn(set, "www-instagram-com.translate.goog"))
        assertEquals("example.org", SiteBlocklist.matchIn(set, "a.example.org"))
        assertNull(SiteBlocklist.matchIn(set, "google.com"))
    }

    @Test
    fun parseSingle_acceptsDomainsAndKeywords() {
        assertEquals(listOf("example.com"), SiteBlocklist.parseSingle("https://www.example.com/x"))
        assertEquals(listOf("instagram"), SiteBlocklist.parseSingle("  Instagram "))
        assertEquals(emptyList<String>(), SiteBlocklist.parseSingle("abc"))
        assertEquals(emptyList<String>(), SiteBlocklist.parseSingle("two words"))
    }

    @Test
    fun hostOf_rejectsNonSites() {
        assertNull(SiteBlocklist.hostOf("hello world"))
        assertNull(SiteBlocklist.hostOf("Search or type web address"))
        assertNull(SiteBlocklist.hostOf("localhost"))
        assertNull(SiteBlocklist.hostOf(""))
        assertNull(SiteBlocklist.hostOf("..com"))
        assertNull(SiteBlocklist.hostOf("0.0.0.0"))
    }

    @Test
    fun parse_supportsHostsAdblockAndPlainLists() {
        val text = """
            # comment line
            127.0.0.1 localhost
            0.0.0.0 bad-one.com
            0.0.0.0 www.bad-two.net # trailing comment
            ::1 ip6-localhost
            ! adblock comment
            [Adblock Plus 2.0]
            ||ads.example.org^
            ||tracker.example.io^${'$'}third-party
            https://site-three.com/page
            four.com, five.com;six.com
            not a domain
        """.trimIndent()
        assertEquals(
            listOf(
                "bad-one.com", "bad-two.net", "ads.example.org", "tracker.example.io",
                "site-three.com", "four.com", "five.com", "six.com",
            ),
            SiteBlocklist.parse(text),
        )
    }

    @Test
    fun parse_dedupes() {
        assertEquals(listOf("a.com"), SiteBlocklist.parse("a.com\nwww.a.com\nhttps://a.com/x"))
    }

    @Test
    fun matchIn_matchesDomainAndSubdomains() {
        val set = setOf("example.com", "deep.other.net")
        assertEquals("example.com", SiteBlocklist.matchIn(set, "example.com"))
        assertEquals("example.com", SiteBlocklist.matchIn(set, "m.example.com"))
        assertEquals("example.com", SiteBlocklist.matchIn(set, "a.b.example.com"))
        assertEquals("deep.other.net", SiteBlocklist.matchIn(set, "x.deep.other.net"))
        assertNull(SiteBlocklist.matchIn(set, "other.net"))
        assertNull(SiteBlocklist.matchIn(set, "notexample.com"))
        assertNull(SiteBlocklist.matchIn(set, "com"))
        assertNull(SiteBlocklist.matchIn(emptySet(), "example.com"))
    }
}
