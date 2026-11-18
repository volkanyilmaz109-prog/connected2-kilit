package com.volkan.c2mblocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.volkan.c2mblocker.BlockerService
import com.volkan.c2mblocker.Sys

/**
 * Tanılama: servis bir tarayıcıda en son neyi okudu. Bir site engellenmediğinde nedenini
 * (adres çubuğu bulunamadı, metin site sayılmadı, listede yok) buradan görmek için.
 */
@Composable
internal fun SiteDiagCard(tick: Int) {
    val ctx = LocalContext.current
    val text = remember(tick) { BlockerService.siteDebug }
    val at = remember(tick) { BlockerService.siteDebugAt }

    SectionCard {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Son okunan adres", style = MaterialTheme.typography.titleMedium)
                if (text == null) {
                    Text(
                        "Servis henüz bir tarayıcı görmedi. Tarayıcıda listedeki bir siteyi açıp " +
                            "buraya dön. Hâlâ boşsa erişilebilirlik servisi çalışmıyor olabilir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        agoText(at),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (text != null) {
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { Sys.copyText(ctx, "VOCD tanılama", text) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Tanılama metnini kopyala")
                }
            }
        }
    }
}

private fun agoText(at: Long): String {
    val sec = ((System.currentTimeMillis() - at) / 1000).coerceAtLeast(0)
    return when {
        sec < 60 -> "$sec sn önce"
        sec < 3600 -> "${sec / 60} dk önce"
        else -> "${sec / 3600} sa önce"
    }
}
