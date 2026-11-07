package com.volkan.c2mblocker

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Ana şifre: rastgele 8 karakter (küçük harf ve rakam), cihazda yalnızca PBKDF2 özeti saklanır.
 * Yanlış denemelerde giderek uzayan bekleme süresi uygulanır.
 */
object Vault {
    const val LENGTH = 8
    // Birbirine benzeyen l / 1 ve o / 0 yok.
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
    private const val ITERATIONS = 20_000
    private const val FREE_ATTEMPTS = 5
    private const val GRACE_MS = 10 * 60_000L

    private val random = SecureRandom()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(BlockerService.PREFS, Context.MODE_PRIVATE)

    fun generate(): String = buildString {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** Kullanıcının yapıştırdığı boşluk ve tireleri yok sayar. */
    fun normalize(input: String): String = input.filter { it.isLetterOrDigit() }

    fun hasPassword(ctx: Context): Boolean = prefs(ctx).contains(BlockerService.KEY_PASS_HASH)

    fun setPassword(ctx: Context, password: String) {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        prefs(ctx).edit()
            .putString(BlockerService.KEY_PASS_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(BlockerService.KEY_PASS_HASH, Base64.encodeToString(hash(normalize(password), salt), Base64.NO_WRAP))
            .putInt(BlockerService.KEY_FAILS, 0)
            .putLong(BlockerService.KEY_LOCK_UNTIL, 0)
            .apply()
    }

    fun verify(ctx: Context, input: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString(BlockerService.KEY_PASS_SALT, null) ?: return false
        val stored = p.getString(BlockerService.KEY_PASS_HASH, null) ?: return false
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val storedBytes = Base64.decode(stored, Base64.NO_WRAP)
        val typed = normalize(input)
        if (MessageDigest.isEqual(hash(typed, saltBytes), storedBytes)) return true
        // Yeni şifreler küçük harf; klavye ilk harfi büyük yazdıysa da kabul et.
        // Önceki sürümlerin büyük-küçük harfli şifreleri yukarıdaki birebir karşılaştırmayla açılır.
        val lower = typed.lowercase()
        return lower != typed && MessageDigest.isEqual(hash(lower, saltBytes), storedBytes)
    }

    fun registerSuccess(ctx: Context) {
        prefs(ctx).edit()
            .putInt(BlockerService.KEY_FAILS, 0)
            .putLong(BlockerService.KEY_LOCK_UNTIL, 0)
            .apply()
        extendGrace(ctx)
    }

    fun registerFailure(ctx: Context) {
        val p = prefs(ctx)
        val fails = p.getInt(BlockerService.KEY_FAILS, 0) + 1
        val edit = p.edit().putInt(BlockerService.KEY_FAILS, fails)
        if (fails >= FREE_ATTEMPTS) {
            val factor = 1L shl minOf(fails - FREE_ATTEMPTS, 7) // 30 sn → en fazla 64 dk
            edit.putLong(BlockerService.KEY_LOCK_UNTIL, System.currentTimeMillis() + 30_000L * factor)
        }
        edit.apply()
    }

    fun lockoutRemainingMs(ctx: Context): Long =
        (prefs(ctx).getLong(BlockerService.KEY_LOCK_UNTIL, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    /** Şifre girildikten sonra sıkı modun ayarları serbest bıraktığı süre. */
    fun graceRemainingMs(ctx: Context): Long =
        (prefs(ctx).getLong(BlockerService.KEY_GRACE_UNTIL, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    fun extendGrace(ctx: Context) {
        prefs(ctx).edit().putLong(BlockerService.KEY_GRACE_UNTIL, System.currentTimeMillis() + GRACE_MS).apply()
    }

    fun endGrace(ctx: Context) {
        prefs(ctx).edit().putLong(BlockerService.KEY_GRACE_UNTIL, 0).apply()
    }

    fun isStrict(ctx: Context): Boolean = prefs(ctx).getBoolean(BlockerService.KEY_STRICT, true)

    fun setStrict(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(BlockerService.KEY_STRICT, on).apply()
    }

    fun targets(ctx: Context): String =
        prefs(ctx).getString(BlockerService.KEY_TARGETS, BlockerService.DEFAULT_TARGETS)!!

    fun setTargets(ctx: Context, value: String) {
        prefs(ctx).edit().putString(BlockerService.KEY_TARGETS, value.trim()).apply()
    }

    fun lastSeenPackage(ctx: Context): String? = prefs(ctx).getString(BlockerService.KEY_LAST_PKG, null)

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
