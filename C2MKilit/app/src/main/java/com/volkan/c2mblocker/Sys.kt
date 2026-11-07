package com.volkan.c2mblocker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/** Sistem ayarları, izin durumları ve pano işlemleri. */
object Sys {
    /** Uygulama kendi açtığı bir ayar ekranına veya dosya seçiciye geçerken kilitlenmesin diye. */
    @Volatile
    var leavingTemporarily = false

    fun isServiceEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val cn = ComponentName(ctx, BlockerService::class.java)
        return enabled.contains(cn.flattenToString()) || enabled.contains(cn.flattenToShortString())
    }

    fun isIgnoringBattery(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    fun openAccessibility(ctx: Context) = open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppInfo(ctx: Context) =
        open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))

    fun requestIgnoreBattery(ctx: Context) {
        if (isIgnoringBattery(ctx)) return
        val ok = open(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
        if (!ok) open(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openAutostart(ctx: Context) {
        val miui = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        if (!open(ctx, miui)) {
            Toast.makeText(ctx, "Otomatik başlatma ekranı yok; uygulama bilgisinden kontrol et", Toast.LENGTH_LONG).show()
            openAppInfo(ctx)
        }
    }

    private fun open(ctx: Context, intent: Intent): Boolean = try {
        leavingTemporarily = true
        Vault.extendGrace(ctx) // şifre girilmiş durumda; ayar ekranlarını sıkı mod engellemesin
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) {
        leavingTemporarily = false
        false
    }

    /** Ağ ayarları: Özel DNS buradan (Xiaomi'de "Bağlantı ve paylaşım" altından) açılır. */
    fun openNetworkSettings(ctx: Context) {
        if (!open(ctx, Intent(Settings.ACTION_WIRELESS_SETTINGS))) open(ctx, Intent(Settings.ACTION_SETTINGS))
    }

    /** Android 9+: Özel DNS'in kullandığı sunucu adı; kapalı ya da "otomatik" ise null. */
    fun privateDnsHost(ctx: Context): String? {
        if (Build.VERSION.SDK_INT < 28) return null
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        return try {
            cm.getLinkProperties(cm.activeNetwork)?.privateDnsServerName
        } catch (e: RuntimeException) {
            null
        }
    }

    fun copyText(ctx: Context, label: String, text: String) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Kopyalandı", Toast.LENGTH_SHORT).show()
    }

    fun copySecret(ctx: Context, text: String) {
        val clip = ClipData.newPlainText("VOCD şifresi", text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true) // Android 13+: önizlemede gizle
        }
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Kopyalandı", Toast.LENGTH_SHORT).show()
    }

    fun pasteText(ctx: Context): String? =
        ctx.getSystemService(ClipboardManager::class.java)?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(ctx)?.toString()
}
