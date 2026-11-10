package com.volkan.c2mblocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * GitHub Releases üzerinden güncelleme. En son sürümün etiketi (ör. "v3.2") yüklü sürümden
 * yeniyse içindeki APK indirilir, aynı paket ve aynı imza olduğu doğrulanır, sonra Android'in
 * kendi kurulum ekranı açılır. Kurulumu kullanıcı onaylar; VOCD sessizce kurulum yapamaz.
 */
object Updater {
    const val REPO = "volkanyilmaz109-prog/connected2-kilit"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val AUTHORITY_SUFFIX = ".updates"

    data class Release(val tag: String, val notes: String, val apkUrl: String, val apkSize: Long)

    /** "v3.2.1" → [3, 2, 1]. Sayıyla başlamayan ilk parçada durur ("3.2-beta" → [3, 2]). */
    internal fun versionParts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '_', '+')
            .map { part -> part.takeWhile { it.isDigit() } }
            .takeWhile { it.isNotEmpty() }
            .map { it.toInt() }

    internal fun isNewer(tag: String, current: String): Boolean {
        val a = versionParts(tag)
        val b = versionParts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Arka plan thread'inde çağır. Yayınlanmış sürüm yoksa null. */
    fun fetchLatest(): Release? {
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "VOCD-Android")
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode !in 200..299) error("GitHub ${conn.responseCode} döndürdü")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            var url: String? = null
            var size = 0L
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            return Release(
                tag = json.optString("tag_name"),
                notes = json.optString("body").trim(),
                apkUrl = url ?: error("Sürümde APK dosyası yok"),
                apkSize = size,
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Arka plan thread'inde çağır. onProgress 0..1 arası değer alır. */
    fun download(ctx: Context, release: Release, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // eski indirmeleri temizle
        val target = File(dir, "VOCD-${release.tag.filter { it.isLetterOrDigit() || it == '.' }}.apk")
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "VOCD-Android")
        try {
            if (conn.responseCode !in 200..299) error("İndirme başarısız (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return target
    }

    /** Sorun yoksa null, varsa kullanıcıya gösterilecek açıklama. */
    fun verify(ctx: Context, file: File): String? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val apk = pm.getPackageArchiveInfo(file.path, flags) ?: return "İndirilen dosya geçerli bir APK değil"
        if (apk.packageName != ctx.packageName) return "İndirilen dosya VOCD değil"
        val mine = pm.getPackageInfo(ctx.packageName, flags)
        val apkSigners = signers(apk)
        // Bazı sürümler arşivin imzasını okuyamıyor; o durumda kontrolü Android'in kurulum
        // aşamasına bırakıyoruz (farklı imzalı APK eski sürümün üstüne zaten kurulmaz).
        if (apkSigners.isNotEmpty() && apkSigners != signers(mine)) return "İmza eşleşmiyor, kurulmadı"
        if (versionCode(apk) <= versionCode(mine)) return "İndirilen sürüm yüklü olandan yeni değil"
        return null
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + AUTHORITY_SUFFIX, file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        Sys.leavingTemporarily = true
        Vault.extendGrace(ctx) // kurulum ekranı VOCD'nin adını gösterir; sıkı mod engellemesin
        ctx.startActivity(intent)
    }

    private fun signers(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.orEmpty().map { sig -> md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
}
