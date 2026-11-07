package com.volkan.c2mblocker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.volkan.c2mblocker.SiteBlocklist
import com.volkan.c2mblocker.Sys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SitesDialog { None, Paste, Download, ClearAll }

@Composable
fun SitesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var single by rememberSaveable { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf(SitesDialog.None) }
    var busy by remember { mutableStateOf<String?>(null) }

    val sites by produceState(initialValue = emptyList<String>(), version) {
        value = withContext(Dispatchers.Default) { SiteBlocklist.sorted(ctx) }
    }
    val q = query.trim().lowercase()
    val shown = remember(sites, q) { if (q.isEmpty()) sites else sites.filter { it.contains(q) } }

    /** Metni arka planda ayrıştırıp listeye ekler; [load] dosya/ağ okuması yapabilir. */
    fun importSites(label: String, load: () -> String) {
        if (busy != null) return
        busy = label
        scope.launch {
            val message = try {
                val (found, added) = withContext(Dispatchers.IO) {
                    val parsed = SiteBlocklist.parse(load())
                    parsed.size to SiteBlocklist.add(ctx, parsed)
                }
                if (found == 0) "Listede geçerli site bulunamadı"
                else "%,d site okundu, %,d yeni site eklendi".format(found, added)
            } catch (e: Exception) {
                "Yüklenemedi: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = null
            version++
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importSites("Dosya okunuyor…") { SiteBlocklist.readUri(ctx, uri) }
    }

    fun addSingle() {
        if (single.isBlank()) return
        val text = single
        single = ""
        importSites("Ekleniyor…") { text }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri")
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("Engelli siteler", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "%,d site".format(sites.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = single,
                    onValueChange = { single = it },
                    label = { Text("Site ekle") },
                    placeholder = { Text("ornek.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { addSingle() }),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = { addSingle() },
                    enabled = single.isNotBlank() && busy == null,
                    modifier = Modifier.heightIn(min = 56.dp).padding(top = 8.dp),
                ) { Text("Ekle") }
            }
        }

        item {
            SectionTitle("Toplu yükle")
        }
        item {
            SectionCard {
                ActionRow(Icons.Rounded.ContentPaste, "Liste yapıştır", "Her satıra bir site, URL veya hosts satırı") {
                    dialog = SitesDialog.Paste
                }
                RowDivider()
                ActionRow(Icons.Rounded.FileOpen, "Dosyadan içe aktar", ".txt, hosts veya adblock listesi") {
                    Sys.leavingTemporarily = true // dosya seçiciye gidince uygulama kilitlenmesin
                    picker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                }
                RowDivider()
                ActionRow(Icons.Rounded.CloudDownload, "İnternetten liste indir", "Hazır listeler veya kendi bağlantın") {
                    dialog = SitesDialog.Download
                }
                AnimatedVisibility(visible = busy != null) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(busy.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitleInline("Liste", Modifier.weight(1f))
                if (sites.isNotEmpty()) {
                    TextButton(onClick = { dialog = SitesDialog.ClearAll }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tümünü sil")
                    }
                }
            }
        }

        if (sites.size > 20) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Listede ara") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Aramayı temizle")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (sites.isEmpty()) {
            item {
                SectionCard {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconBadge(Icons.Rounded.Public, MaterialTheme.colorScheme.primary, size = 56)
                        Text("Henüz site yok", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tek tek ekleyebilir ya da hazır bir listeyi toplu yükleyebilirsin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else if (shown.isEmpty()) {
            item {
                Text(
                    "\"$query\" ile eşleşen site yok",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        } else {
            items(shown, key = { it }) { domain ->
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            domain,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        IconButton(onClick = {
                            SiteBlocklist.remove(ctx, domain)
                            version++
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "$domain sitesini listeden çıkar")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    when (dialog) {
        SitesDialog.Paste -> PasteDialog(
            onDismiss = { dialog = SitesDialog.None },
            onImport = { text -> dialog = SitesDialog.None; importSites("Liste işleniyor…") { text } },
        )
        SitesDialog.Download -> DownloadDialog(
            onDismiss = { dialog = SitesDialog.None },
            onImport = { url -> dialog = SitesDialog.None; importSites("Liste indiriliyor…") { SiteBlocklist.download(url) } },
        )
        SitesDialog.ClearAll -> AlertDialog(
            onDismissRequest = { dialog = SitesDialog.None },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text("Tüm siteler silinsin mi?") },
            text = { Text("%,d site listeden çıkarılacak. Bu geri alınamaz.".format(sites.size)) },
            confirmButton = {
                TextButton(onClick = {
                    SiteBlocklist.clear(ctx)
                    version++
                    dialog = SitesDialog.None
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = SitesDialog.None }) { Text("Vazgeç") } },
        )
        SitesDialog.None -> Unit
    }
}

@Composable
private fun SectionTitleInline(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp),
    )
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null) },
        title = { Text("Liste yapıştır") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("ornek.com\nhttps://site.net/sayfa\n0.0.0.0 reklam.org") },
                    minLines = 6,
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { Sys.pasteText(ctx)?.let { text = it } }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Panodan yapıştır")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("Ekle") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun DownloadDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val validUrl = url.trim().let { it.startsWith("https://") || it.startsWith("http://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
        title = { Text("İnternetten liste indir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hazır listeler", style = MaterialTheme.typography.labelLarge)
                SiteBlocklist.PRESETS.forEach { preset ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onImport(preset.url) }
                            .heightIn(min = 56.dp)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                preset.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Kendi liste bağlantın") },
                    placeholder = { Text("https://…/hosts.txt") },
                    singleLine = true,
                    isError = url.isNotBlank() && !validUrl,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(url.trim()) }, enabled = validUrl) { Text("İndir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
