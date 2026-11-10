package com.volkan.c2mblocker.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import com.volkan.c2mblocker.BuildConfig
import com.volkan.c2mblocker.SiteBlocklist
import com.volkan.c2mblocker.Sys
import com.volkan.c2mblocker.Vault
import kotlinx.coroutines.delay

private enum class Screen { Setup, Locked, Dashboard, Sites }

@Composable
fun C2MApp() {
    val ctx = LocalContext.current
    var hasPassword by remember { mutableStateOf(Vault.hasPassword(ctx)) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var sitesOpen by rememberSaveable { mutableStateOf(false) }

    // Uygulamadan çıkınca (kendi açtığımız ayar ekranları hariç) hemen kilitlen.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (Sys.leavingTemporarily) Sys.leavingTemporarily = false else unlocked = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (unlocked && Vault.graceRemainingMs(ctx) == 0L) unlocked = false
    }

    val screen = when {
        !hasPassword -> Screen.Setup
        !unlocked -> Screen.Locked
        sitesOpen -> Screen.Sites
        else -> Screen.Dashboard
    }
    BackHandler(enabled = screen == Screen.Sites) { sitesOpen = false }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Setup -> SetupScreen(onDone = { hasPassword = true; unlocked = true })
                Screen.Locked -> LockScreen(onUnlock = { unlocked = true })
                Screen.Dashboard -> DashboardScreen(onLock = { unlocked = false }, onOpenSites = { sitesOpen = true })
                Screen.Sites -> SitesScreen(onBack = { sitesOpen = false })
            }
        }
    }
}

// ==================================================================== Kurulum

@Composable
private fun SetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var password by rememberSaveable { mutableStateOf(Vault.generate()) }
    var saved by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconBadge(Icons.Rounded.Key, MaterialTheme.colorScheme.primary, size = 64)
        Text("Ana şifreni oluştur", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Bu şifre olmadan VOCD açılmaz ve sıkı mod kapatılamaz. Şifre bir daha " +
                "gösterilmeyecek; şimdi kopyala ve güvenli bir yere kaydet. Kendi elinin " +
                "kolay ulaşamayacağı bir yer olsun: güvendiğin birine gönder ya da bir şifre " +
                "yöneticisine koy.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SecretBox(password)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = { Sys.copySecret(ctx, password) },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kopyala")
            }
            OutlinedButton(
                onClick = { password = Vault.generate(); saved = false },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Yenisini üret")
            }
        }

        CheckRow(saved, onChange = { saved = it }, text = "Şifreyi güvenli bir yere kaydettim")

        Button(
            onClick = {
                Vault.setPassword(ctx, password)
                Vault.setStrict(ctx, true)
                Vault.registerSuccess(ctx)
                onDone()
            },
            enabled = saved,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Şifreyi kaydet ve devam et") }
    }
}

@Composable
private fun SecretBox(password: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            password.chunked(4).joinToString("  "),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        )
    }
}

@Composable
internal fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

// ==================================================================== Kilit

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lockMs by remember { mutableLongStateOf(Vault.lockoutRemainingMs(ctx)) }

    LaunchedEffect(lockMs > 0) {
        while (lockMs > 0) {
            delay(1000)
            lockMs = Vault.lockoutRemainingMs(ctx)
        }
    }

    fun attempt() {
        if (lockMs > 0 || input.isBlank()) return
        if (Vault.verify(ctx, input)) {
            Vault.registerSuccess(ctx)
            onUnlock()
        } else {
            Vault.registerFailure(ctx)
            lockMs = Vault.lockoutRemainingMs(ctx)
            error = "Şifre yanlış"
            input = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Spacer(Modifier.height(48.dp))
        IconBadge(Icons.Rounded.Lock, MaterialTheme.colorScheme.primary, size = 88)
        Text("VOCD kilitli", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Devam etmek için ana şifreni gir.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))

        val locked = lockMs > 0
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null },
            label = { Text("Ana şifre") },
            singleLine = true,
            enabled = !locked,
            isError = error != null,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { attempt() }),
            supportingText = {
                when {
                    locked -> Text("Çok fazla yanlış deneme. ${(lockMs + 999) / 1000} sn bekle.")
                    error != null -> Text(error!!)
                }
            },
            trailingIcon = {
                Row {
                    IconButton(onClick = { Sys.pasteText(ctx)?.let { input = it; error = null } }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Panodan yapıştır")
                    }
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (visible) "Şifreyi gizle" else "Şifreyi göster",
                        )
                    }
                }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { attempt() },
            enabled = !locked && input.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Rounded.LockOpen, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Kilidi aç")
        }
    }
}

// ==================================================================== Panel

private enum class Tab(val label: String, val icon: ImageVector) {
    App("C2 Kilidi", Icons.Rounded.Shield),
    Sites("Siteler", Icons.Rounded.Public),
    Settings("Ayarlar", Icons.Rounded.Settings),
}

@Composable
private fun DashboardScreen(onLock: () -> Unit, onOpenSites: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val serviceOn = remember(tick) { Sys.isServiceEnabled(ctx) }
    var strict by remember { mutableStateOf(Vault.isStrict(ctx)) }
    var tab by rememberSaveable { mutableStateOf(Tab.App) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { inner ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "tab",
            modifier = Modifier.padding(inner),
        ) { t ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VOCD", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    FilledTonalIconButton(onClick = onLock, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Uygulamayı kilitle")
                    }
                }
                when (t) {
                    Tab.App -> AppTab(serviceOn = serviceOn, strict = strict)
                    Tab.Sites -> SitesTab(tick = tick, onOpenSites = onOpenSites)
                    Tab.Settings -> SettingsTab(
                        tick = tick,
                        serviceOn = serviceOn,
                        strict = strict,
                        onStrictChange = { strict = it; Vault.setStrict(ctx, it) },
                        onLock = onLock,
                    )
                }
            }
        }
    }
}

/** connected2.me sekme kilidi: durum, kilitlenen sekmeler ve hedef paket. */
@Composable
private fun AppTab(serviceOn: Boolean, strict: Boolean) {
    StatusHero(serviceOn = serviceOn, strict = strict)

    SectionTitle("Kilitli sekmeler")
    SectionCard {
        InfoRow(Icons.Rounded.Explore, "Keşfet", "Dokunma engellenir")
        RowDivider()
        InfoRow(Icons.Rounded.Shuffle, "Shuffle ve arama", "Dokunma engellenir, açılırsa Sohbet'e döner")
    }

    SectionTitle("Hedef uygulama")
    TargetCard()
}

/** Site engelleme: tarayıcı kontrolü, engelli liste ve sistem genelinde DNS filtresi. */
@Composable
private fun SitesTab(tick: Int, onOpenSites: () -> Unit) {
    val ctx = LocalContext.current
    val siteCount = remember(tick) { SiteBlocklist.all(ctx).size }
    val siteBlocks = remember(tick) { SiteBlocklist.blockCount(ctx) }
    var sitesOn by remember { mutableStateOf(SiteBlocklist.isEnabled(ctx)) }

    SectionCard {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = sitesOn,
                    onValueChange = { sitesOn = it; SiteBlocklist.setEnabled(ctx, it) },
                    role = Role.Switch,
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Rounded.Language, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Tarayıcılarda engelle", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Chrome, Samsung İnternet, Firefox, Opera, Edge, Brave ve Mi Tarayıcı'da listedeki siteler açılmaz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = sitesOn, onCheckedChange = null)
        }
        RowDivider()
        ActionRow(
            Icons.Rounded.Public,
            "Engelli siteler",
            "%,d site · %,d kez engellendi".format(siteCount, siteBlocks),
            onClick = onOpenSites,
        )
    }

    SectionTitle("Tüm uygulamalarda filtre")
    PrivateDnsCard(tick)
}

/** Kurulum, sıkı mod ve şifre. */
@Composable
private fun SettingsTab(
    tick: Int,
    serviceOn: Boolean,
    strict: Boolean,
    onStrictChange: (Boolean) -> Unit,
    onLock: () -> Unit,
) {
    val ctx = LocalContext.current
    val batteryOk = remember(tick) { Sys.isIgnoringBattery(ctx) }
    val graceMs = remember(tick) { Vault.graceRemainingMs(ctx) }
    var showNewPassword by remember { mutableStateOf(false) }

    SectionTitle("Kurulum")
    SectionCard {
        ChecklistRow(
            icon = Icons.Rounded.Accessibility,
            title = "Erişilebilirlik servisi",
            ok = serviceOn,
            detail = if (serviceOn) "Açık, arka planda çalışıyor" else "Kapalı, engelleme çalışmıyor",
            action = "Aç",
            onClick = { Sys.openAccessibility(ctx) },
        )
        RowDivider()
        ChecklistRow(
            icon = Icons.Rounded.BatteryChargingFull,
            title = "Pil kısıtlaması",
            ok = batteryOk,
            detail = if (batteryOk) "Kısıtlama yok" else "Sistem servisi durdurabilir",
            action = "Kaldır",
            onClick = { Sys.requestIgnoreBattery(ctx) },
        )
        RowDivider()
        ChecklistRow(
            icon = Icons.Rounded.RestartAlt,
            title = "Otomatik başlatma",
            ok = null,
            detail = "Xiaomi'de açık olmalı",
            action = "Kontrol et",
            onClick = { Sys.openAutostart(ctx) },
        )
        RowDivider()
        ChecklistRow(
            icon = Icons.Rounded.AdminPanelSettings,
            title = "Kısıtlı ayarlar",
            ok = if (serviceOn) true else null,
            detail = if (serviceOn) "İzin verilmiş" else "Servis gri ise ⋮ menüsünden izin ver",
            action = "Uygulama bilgisi",
            onClick = { Sys.openAppInfo(ctx) },
        )
    }

    SectionTitle("Sıkı mod")
    StrictCard(
        strict = strict,
        graceMs = graceMs,
        onStrictChange = onStrictChange,
        onLockNow = { Vault.endGrace(ctx); onLock() },
    )

    SectionTitle("Şifre")
    SectionCard {
        ActionRow(Icons.Rounded.Key, "Yeni şifre oluştur", "Eski şifre hemen geçersiz olur") {
            showNewPassword = true
        }
    }

    SectionTitle("Güncelleme")
    UpdateCard()

    Text(
        "Sürüm ${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    )

    if (showNewPassword) NewPasswordDialog(onDismiss = { showNewPassword = false })
}

@Composable
private fun StatusHero(serviceOn: Boolean, strict: Boolean) {
    // Gradyan renkleri temadan bağımsız: beyaz yazıyla iki temada da yeterli kontrast.
    val brush = if (serviceOn) {
        Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF0F766E)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFB91C1C), Color(0xFF9A3412)))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brush)
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (serviceOn) Icons.Rounded.VerifiedUser else Icons.Rounded.GppBad,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                if (serviceOn) "Koruma aktif" else "Koruma kapalı",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                if (serviceOn) "connected2.me'de Keşfet ve Shuffle kilitli, listedeki siteler engelli."
                else "Erişilebilirlik servisini açmadan engelleme çalışmaz.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.88f),
            )
            if (serviceOn) {
                Pill(if (strict) "Sıkı mod açık" else "Sıkı mod kapalı", Icons.Rounded.Shield)
            }
        }
    }
}

@Composable
private fun Pill(text: String, icon: ImageVector) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun StrictCard(strict: Boolean, graceMs: Long, onStrictChange: (Boolean) -> Unit, onLockNow: () -> Unit) {
    SectionCard {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = strict, onValueChange = onStrictChange, role = Role.Switch)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Rounded.Shield, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Ayarları koru", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Kilitliyken VOCD'nin uygulama bilgisi, erişilebilirlik sayfası ve kaldırma ekranı açılmaz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = strict, onCheckedChange = null)
        }
        AnimatedVisibility(visible = strict && graceMs > 0) {
            Column {
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Ayarlar ${formatMs(graceMs)} daha serbest",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onLockNow, modifier = Modifier.heightIn(min = 48.dp)) { Text("Şimdi kilitle") }
                }
            }
        }
    }
}

@Composable
private fun TargetCard() {
    val ctx = LocalContext.current
    var targets by rememberSaveable { mutableStateOf(Vault.targets(ctx)) }
    var last by remember { mutableStateOf(Vault.lastSeenPackage(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { last = Vault.lastSeenPackage(ctx) }

    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = targets,
                onValueChange = { targets = it },
                label = { Text("Paket adı (virgülle ayır)") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Son görülen uygulama: ${last ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val pkg = last ?: return@OutlinedButton
                        val list = targets.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (pkg !in list) targets = (list + pkg).joinToString(",")
                    },
                    enabled = last != null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Son göreni ekle") }
                Button(
                    onClick = {
                        Vault.setTargets(ctx, targets)
                        Toast.makeText(ctx, "Kaydedildi", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Kaydet") }
            }
        }
    }
}

@Composable
private fun NewPasswordDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var password by rememberSaveable { mutableStateOf(Vault.generate()) }
    var saved by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, contentDescription = null) },
        title = { Text("Yeni ana şifre") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SecretBox(password)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { Sys.copySecret(ctx, password) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Kopyala")
                    }
                    OutlinedButton(onClick = { password = Vault.generate(); saved = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Yenile")
                    }
                }
                CheckRow(saved, onChange = { saved = it }, text = "Kaydettim")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Vault.setPassword(ctx, password)
                    Toast.makeText(ctx, "Şifre değiştirildi", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                enabled = saved,
            ) { Text("Değiştir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

// ==================================================================== Ortak parçalar

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    )
}

@Composable
internal fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun IconBadge(icon: ImageVector, tint: Color, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.Block, contentDescription = "Engelli", tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun ChecklistRow(
    icon: ImageVector,
    title: String,
    ok: Boolean?,
    detail: String,
    action: String,
    onClick: () -> Unit,
) {
    val statusColor = when (ok) {
        true -> MaterialTheme.colorScheme.tertiary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, statusColor)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ok != null) {
                    Icon(
                        if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (ok != true) {
            Spacer(Modifier.width(8.dp))
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms + 999) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
