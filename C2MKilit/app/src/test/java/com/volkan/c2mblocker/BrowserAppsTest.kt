package com.volkan.c2mblocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAppsTest {
    @Test
    fun addressBarLikeViewsAreRecognised() {
        assertTrue(BrowserApps.looksLikeAddressBar("com.mi.globalbrowser:id/url_text", "android.widget.TextView"))
        assertTrue(BrowserApps.looksLikeAddressBar("org.example.browser:id/Omnibox_View", null))
        assertTrue(BrowserApps.looksLikeAddressBar(null, "android.widget.EditText"))
        assertTrue(BrowserApps.looksLikeAddressBar(null, "androidx.appcompat.widget.AppCompatEditText"))
    }

    @Test
    fun pageContentIsIgnored() {
        assertFalse(BrowserApps.looksLikeAddressBar(null, "android.view.View"))
        assertFalse(BrowserApps.looksLikeAddressBar("com.android.chrome:id/tab_title", "android.widget.TextView"))
        assertFalse(BrowserApps.looksLikeAddressBar(null, null))
    }
}
