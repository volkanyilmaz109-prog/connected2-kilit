package com.volkan.c2mblocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.volkan.c2mblocker.BuildConfig
import com.volkan.c2mblocker.Sys
import com.volkan.c2mblocker.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val release: Updater.Release, val progress: Float) : UpdateState
    data class Ready(val release: Updater.Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
internal fun UpdateCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    fun check() {
        state = UpdateState.Checking
        scope.launch {
            state = try {
                val latest = withContext(Dispatchers.IO) { Updater.fetchLatest() }
                when {
                    latest == null -> UpdateState.UpToDate
                    Updater.isNewer(latest.tag, BuildConfig.VERSION_NAME) -> UpdateState.Available(latest)
                    else -> UpdateState.UpToDate
                }
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "Bağlantı kurulamadı")
            }
        }
    }

    fun download(release: Updater.Release) {
        state = UpdateState.Downloading(release, 0f)
        scope.launch {
            state = try {
                val file = withContext(Dispatchers.IO) {
                    Updater.download(ctx, release) { p ->
                        scope.launch { state = UpdateState.Downloading(release, p) }
                    }
                }
                val problem = withContext(Dispatchers.IO) { Updater.verify(ctx, file) }
                if (problem != null) UpdateState.Failed(problem) else UpdateState.Ready(release, file)
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "İndirme başarısız")
            }
        }
    }

    fun install(file: File) {
        if (!Updater.canInstall(ctx)) {
            Sys.openUnknownSources(ctx) // izin verilip geri dönülünce "Kur"a tekrar basılır
            return
        }
        Updater.install(ctx, file)
    }

    SectionCard {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Rounded.SystemUpdate, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Güncelleme", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (val s = state) {
                        UpdateState.Idle -> "Yüklü sürüm ${BuildConfig.VERSION_NAME}"
                        UpdateState.Checking -> "GitHub'a bakılıyor…"
                        UpdateState.UpToDate -> "En son sürümü kullanıyorsun (${BuildConfig.VERSION_NAME})"
                        is UpdateState.Available -> "Yeni sürüm var: ${s.release.tag}"
                        is UpdateState.Downloading -> "İndiriliyor: %${(s.progress * 100).toInt()}"
                        is UpdateState.Ready -> "${s.release.tag} indirildi ve doğrulandı"
                        is UpdateState.Failed -> s.message
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state is UpdateState.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is UpdateState.Downloading -> LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp))
                is UpdateState.Available -> if (s.release.notes.isNotEmpty()) {
                    Text(
                        s.release.notes.take(600),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Unit
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val s = state) {
                    is UpdateState.Available -> ActionButton("İndir", Icons.Rounded.CloudDownload) { download(s.release) }
                    is UpdateState.Ready -> ActionButton("Kur", Icons.Rounded.SystemUpdate) { install(s.file) }
                    is UpdateState.Downloading, UpdateState.Checking -> Unit
                    else -> ActionButton("Güncellemeleri kontrol et", Icons.Rounded.SystemUpdate) { check() }
                }
                OutlinedButton(
                    onClick = { Sys.openUrl(ctx, Updater.RELEASES_PAGE) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("GitHub") }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
