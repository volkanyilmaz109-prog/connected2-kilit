package com.volkan.c2mblocker

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Engelli site listesi. On binlerce alan adı olabileceği için SharedPreferences yerine
 * uygulama klasöründe düz metin dosyası olarak tutulur ve bellekte HashSet olarak önbelleğe alınır.
 * Servis ve arayüz aynı süreçte çalıştığı için önbellek ikisi arasında ortak.
 */
object SiteBlocklist {
    private const val FILE_NAME = "blocked_sites.txt"
    private const val KEY_BLOCK_COUNT = "site_block_count"
    private const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

    /** Hosts dosyalarında site olmayan, atlanması gereken girişler. */
    private val SKIP = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost",
        "ip6-loopback", "ip6-localnet", "ip6-mcastprefix", "ip6-allnodes", "ip6-allrouters",
        "ip6-allhosts", "0.0.0.0",
    )

    data class Preset(val title: String, val detail: String, val url: String)

    val PRESETS = listOf(
        Preset(
            "Yetişkin içerik",
            "StevenBlack · yaklaşık 77 bin site",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
        ),
        Preset(
            "Kumar ve bahis",
            "StevenBlack · kumar listesi",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling-only/hosts",
        ),
    )

    @Volatile
    private var cache: Set<String>? = null

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(BlockerService.PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): Set<String> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val f = file(ctx)
            val loaded: Set<String> = if (f.exists()) {
                f.useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() }.toHashSet() }
            } else {
                emptySet()
            }
            cache = loaded
            return loaded
        }
    }

    fun sorted(ctx: Context): List<String> = all(ctx).sorted()

    /** Listeye ekler, gerçekten yeni olan site sayısını döndürür. */
    fun add(ctx: Context, domains: Collection<String>): Int = synchronized(this) {
        val set = HashSet(all(ctx))
        val before = set.size
        set.addAll(domains)
        if (set.size != before) save(ctx, set)
        set.size - before
    }

    fun remove(ctx: Context, domain: String) = synchronized(this) {
        val set = HashSet(all(ctx))
        if (set.remove(domain)) save(ctx, set)
    }

    fun clear(ctx: Context) = synchronized(this) { save(ctx, emptySet()) }

    private fun save(ctx: Context, set: Set<String>) {
        val target = file(ctx)
        val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
        tmp.bufferedWriter().use { w -> set.forEach { w.write(it); w.newLine() } }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        cache = HashSet(set)
    }

    @JvmStatic
    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(BlockerService.KEY_SITES_ON, true)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(BlockerService.KEY_SITES_ON, on).apply()
    }

    @JvmStatic
    fun countBlock(ctx: Context) {
        val p = prefs(ctx)
        p.edit().putInt(KEY_BLOCK_COUNT, p.getInt(KEY_BLOCK_COUNT, 0) + 1).apply()
    }

    fun blockCount(ctx: Context): Int = prefs(ctx).getInt(KEY_BLOCK_COUNT, 0)

    /**
     * Alan adı ya da üst alan adlarından biri listedeyse eşleşen girişi döndürür:
     * "m.ornek.com" için "m.ornek.com" ve "ornek.com" denenir, tek başına "com" denenmez.
     */
    @JvmStatic
    fun match(ctx: Context, host: String): String? = matchIn(all(ctx), host)

    internal fun matchIn(set: Set<String>, host: String): String? {
        if (set.isEmpty()) return null
        var h = host
        while (h.contains('.')) {
            if (h in set) return h
            h = h.substringAfter('.')
        }
        // Noktasız girişler anahtar kelime: adresin herhangi bir yerinde geçerse engellenir
        // ("instagram" → instagram.com, m.instagram.com, instagram-com.translate.goog ...).
        return keywordsOf(set).firstOrNull { host.contains(it) }
    }

    /** En kısa anahtar kelime; daha kısası çok fazla siteyi yanlışlıkla kapatır. */
    const val MIN_KEYWORD = 4

    @Volatile
    private var keywordCache: Pair<Set<String>, List<String>>? = null

    private fun keywordsOf(set: Set<String>): List<String> {
        keywordCache?.let { if (it.first === set) return it.second }
        val k = set.filter { '.' !in it }
        keywordCache = set to k
        return k
    }

    /**
     * Elle tek giriş: alan adı ya da URL ise alan adı, nokta içermeyen tek bir sözcükse
     * (ör. "instagram") anahtar kelime olarak eklenir.
     */
    fun parseSingle(text: String): List<String> {
        val parsed = parse(text)
        if (parsed.isNotEmpty()) return parsed
        val word = text.trim().lowercase().removePrefix("www.")
        val ok = word.length >= MIN_KEYWORD && word !in SKIP &&
            word.all { it.isLetterOrDigit() || it == '-' }
        return if (ok) listOf(word) else emptyList()
    }

    /** Adres çubuğu metninden ya da bir URL'den alan adını çıkarır; site değilse null. */
    @JvmStatic
    fun hostOf(text: String): String? {
        // Adres çubukları yön işaretleri gibi görünmez karakterler ekleyebiliyor; onları at.
        var s = text.filterNot { Character.getType(it) == Character.FORMAT.toInt() || it == '\u00A0' }
            .trim().lowercase()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        s = s.substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfter('@')
            .substringBefore(':')
            .trimEnd('.')
            .removePrefix("www.")
        val valid = s.contains('.') && !s.startsWith('.') && !s.contains("..") &&
            s.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        return if (valid && s !in SKIP) s else null
    }

    /**
     * Toplu listeyi ayrıştırır. Desteklenen biçimler:
     * - her satırda bir alan adı veya URL (virgül / boşlukla ayrılmış da olabilir)
     * - hosts dosyası: "0.0.0.0 ornek.com"
     * - adblock: "||ornek.com^"
     * "#" sonrası ve "!" ile başlayan satırlar yorum sayılır.
     */
    fun parse(text: String): List<String> {
        val out = LinkedHashSet<String>()
        text.lineSequence().forEach { raw ->
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach
            line = line.substringBefore('#').trim()
            if (line.startsWith("||")) line = line.removePrefix("||").substringBefore('^')
            val tokens = line.split(' ', '\t', ',', ';').filter { it.isNotBlank() }
            val rest = if (tokens.isNotEmpty() && looksLikeIp(tokens[0])) tokens.drop(1) else tokens
            rest.forEach { token -> hostOf(token)?.let(out::add) }
        }
        return out.toList()
    }

    private fun looksLikeIp(s: String): Boolean =
        s.all { it.isDigit() || it == '.' } && s.count { it == '.' } == 3 ||
            s.contains(':') && s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }

    /** Arka plan thread'inde çağır (dosya okur). */
    fun readUri(ctx: Context, uri: Uri): String {
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı")
        return input.use { readLimited(it) }
    }

    /** Arka plan thread'inde çağır (ağdan indirir). */
    fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) error("Sunucu ${conn.responseCode} döndürdü")
            return conn.inputStream.use { readLimited(it) }
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_IMPORT_BYTES) error("Dosya çok büyük (en fazla 32 MB)")
            bytes.write(buf, 0, n)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }
}
