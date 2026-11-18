package com.volkan.c2mblocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock

/**
 * Telefonda kurulu tüm tarayıcılar: bir web adresini açabilen her uygulama. Sabit listede
 * olmayan ya da adres çubuğunun kimliği değişmiş bir tarayıcı da böylece gözden kaçmaz.
 */
object BrowserApps {
    private const val CACHE_MS = 60_000L

    @Volatile
    private var cached: Set<String> = emptySet()

    @Volatile
    private var cachedAt = Long.MIN_VALUE / 2

    /** Adres çubuğu olabilecek görünüm kimliklerindeki sözcükler. */
    private val ADDRESS_WORDS = listOf(
        "url", "address", "omnibox", "omnibar", "location", "toolbar", "search_box", "searchbox",
        "search_bar", "addressbar", "title_bar", "domain",
    )

    @JvmStatic
    fun packages(ctx: Context): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (now - cachedAt < CACHE_MS) return cached
        val found = HashSet<String>()
        try {
            val pm = ctx.packageManager
            for (scheme in listOf("https", "http")) {
                val probe = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://example.com/"))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
                    .mapNotNullTo(found) { it.activityInfo?.packageName }
            }
        } catch (e: RuntimeException) {
            // Paket listesi okunamazsa son bilineni kullan.
            return cached
        }
        found.remove(ctx.packageName)
        cached = found
        cachedAt = now
        return found
    }

    @JvmStatic
    fun isBrowser(ctx: Context, pkg: String): Boolean = pkg in packages(ctx)

    /**
     * Tanımadığımız bir tarayıcıda bu görünüm adres çubuğu olabilir mi? Yazı alanları ya da
     * kimliğinde adres çubuğu sözcüğü geçen görünümler. Sayfa içeriği (WebView metni) elenir.
     */
    @JvmStatic
    fun looksLikeAddressBar(viewId: String?, className: CharSequence?): Boolean {
        if (className != null && className.endsWith("EditText")) return true
        val id = viewId?.substringAfter(":id/")?.lowercase() ?: return false
        return ADDRESS_WORDS.any { id.contains(it) }
    }
}
