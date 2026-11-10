package com.volkan.c2mblocker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Sekmelerin başındaki gradyan kart; sağ üstte logonun V'si silik filigran olarak durur. */
@Composable
internal fun HeroCard(brush: Brush, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brush),
    ) {
        VMark(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(156.dp))
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Dekoratif: ekran okuyucuya kapalı. */
@Composable
private fun VMark(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val s = size.minDimension / 108f
        val path = Path().apply {
            moveTo(37f * s, 39f * s)
            lineTo(54f * s, 71f * s)
            lineTo(71f * s, 39f * s)
        }
        drawPath(
            path,
            color = Brand.OnHero.copy(alpha = 0.14f),
            style = Stroke(width = 9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun HeroIcon(icon: ImageVector) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(Brand.HeroChip),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Brand.OnHero, modifier = Modifier.size(32.dp))
    }
}

@Composable
internal fun HeroPill(text: String, icon: ImageVector) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brand.HeroChip)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Brand.OnHero, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Brand.OnHero)
    }
}

@Composable
internal fun StatusHero(serviceOn: Boolean, strict: Boolean) {
    HeroCard(if (serviceOn) Brand.active else Brand.alert) {
        HeroIcon(if (serviceOn) Icons.Rounded.VerifiedUser else Icons.Rounded.GppBad)
        Text(
            if (serviceOn) "Koruma aktif" else "Koruma kapalı",
            style = MaterialTheme.typography.headlineMedium,
            color = Brand.OnHero,
        )
        Text(
            if (serviceOn) "connected2.me'de Keşfet ve Shuffle kilitli."
            else "Erişilebilirlik servisini açmadan kilit çalışmaz. Ayarlar sekmesinden aç.",
            style = MaterialTheme.typography.bodyLarge,
            color = Brand.OnHeroMuted,
        )
        if (serviceOn) HeroPill(if (strict) "Sıkı mod açık" else "Sıkı mod kapalı", Icons.Rounded.Shield)
    }
}

@Composable
internal fun SitesHero(siteCount: Int, blockCount: Int, browserOn: Boolean, dnsOn: Boolean) {
    HeroCard(if (browserOn || dnsOn) Brand.active else Brand.alert) {
        HeroIcon(Icons.Rounded.Public)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            HeroStat("%,d".format(siteCount), "engelli site")
            HeroStat("%,d".format(blockCount), "kez engellendi")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroPill(if (browserOn) "Tarayıcı açık" else "Tarayıcı kapalı", Icons.Rounded.Public)
            HeroPill(if (dnsOn) "Özel DNS açık" else "Özel DNS kapalı", Icons.Rounded.Dns)
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displaySmall, color = Brand.OnHero)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Brand.OnHeroMuted)
    }
}
