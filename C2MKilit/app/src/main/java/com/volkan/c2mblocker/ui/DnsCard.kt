package com.volkan.c2mblocker.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.volkan.c2mblocker.Sys

/**
 * Sistem genelinde site filtresi. Android'in kendi "Özel DNS" ayarı (DNS-over-TLS) aile filtreli
 * bir sunucuya yönlendirilince yetişkin siteler her tarayıcıda ve her uygulamada açılmaz.
 * VOCD bu ayarı kendisi değiştiremez; adresi kopyalatır, ayarları açar ve durumu gösterir.
 */
internal object DnsFilters {
    data class Filter(val name: String, val detail: String, val host: String)

    val ALL = listOf(
        Filter(
            "CleanBrowsing Aile",
            "En sıkısı: yetişkin içerik, VPN / proxy siteleri ve karışık içerikli siteler (ör. Reddit) " +
                "kapanır; Google, Bing ve YouTube güvenli modda açılır.",
            "family-filter-dns.cleanbrowsing.org",
        ),
        Filter(
            "CleanBrowsing Yetişkin",
            "Yetişkin içerik kapanır, Google ve Bing güvenli aramada açılır. Reddit gibi siteler açık kalır.",
            "adult-filter-dns.cleanbrowsing.org",
        ),
        Filter(
            "Cloudflare Aile",
            "Yetişkin içerik ve zararlı yazılım siteleri kapanır.",
            "family.cloudflare-dns.com",
        ),
    )
}

@Composable
internal fun PrivateDnsCard(tick: Int) {
    val ctx = LocalContext.current
    val supported = Build.VERSION.SDK_INT >= 28
    val host = remember(tick) { Sys.privateDnsHost(ctx) }
    val active = DnsFilters.ALL.firstOrNull { it.host.equals(host, ignoreCase = true) }

    SectionCard {
        ChecklistRow(
            icon = Icons.Rounded.Dns,
            title = "Özel DNS filtresi",
            ok = when {
                active != null -> true
                host != null -> null
                else -> false
            },
            detail = when {
                !supported -> "Android 9 ve üstünde var"
                active != null -> "Açık: ${active.name}"
                host != null -> "Başka bir sunucu: $host"
                else -> "Kapalı"
            },
            action = "Ayarları aç",
            onClick = { Sys.openNetworkSettings(ctx) },
        )
        RowDivider()
        Text(
            "Tarayıcı fark etmeksizin tüm telefonda çalışır. Aşağıdan bir adres kopyala, sonra " +
                "Ayarlar → Bağlantı ve paylaşım → Özel DNS → \"Özel DNS sağlayıcı ana makine adı\" " +
                "seçeneğine yapıştırıp kaydet. Tarayıcının kendi \"Güvenli DNS\" ayarında başka bir " +
                "sağlayıcı seçili olmasın.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        )
        DnsFilters.ALL.forEach { filter ->
            RowDivider()
            FilterRow(filter, inUse = filter == active)
        }
    }
}

@Composable
private fun FilterRow(filter: DnsFilters.Filter, inUse: Boolean) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = 72.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(filter.name, style = MaterialTheme.typography.titleMedium)
                if (inUse) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Kullanılıyor",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                filter.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    filter.host,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = { Sys.copyText(ctx, "Özel DNS", filter.host) }) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "${filter.name} adresini kopyala")
        }
    }
}
