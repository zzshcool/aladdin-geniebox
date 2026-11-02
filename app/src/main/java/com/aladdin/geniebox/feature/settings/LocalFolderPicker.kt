package com.aladdin.geniebox.feature.settings

import android.os.Build
import android.os.storage.StorageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.DialogProperties
import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import com.aladdin.geniebox.R
import com.aladdin.geniebox.app.AppLanguage
import com.aladdin.geniebox.ui.component.FocusableButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LocalFolderPicker(onDismiss: () -> Unit, onSelect: (File) -> Unit) {
    val context = LocalContext.current
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val firstFolderFocus = remember { FocusRequester() }
    var folder by remember { mutableStateOf<File?>(null) }
    var directories by remember { mutableStateOf<List<File>>(emptyList()) }
    var readable by remember { mutableStateOf(false) }
    var volumeNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val listState = rememberLazyListState()
    LaunchedEffect(folder) {
        val result = withContext(Dispatchers.IO) {
            val current = folder
            if (current == null) {
                val volumes = if (Build.VERSION.SDK_INT >= 30) {
                    context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { volume ->
                        volume.directory?.let { it to volume.getDescription(context) }
                    }
                } else {
                    (listOf(android.os.Environment.getExternalStorageDirectory()) + File("/storage").listFiles().orEmpty())
                        .map { it to it.name }
                }
                val available = volumes.filter { it.first.isDirectory && it.first.canRead() }
                    .distinctBy { it.first.canonicalPath }
                Triple(available.map { it.first }, false, available.associate { it.first.path to it.second })
            } else {
                current.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.sortedBy { it.name.lowercase() }?.let { Triple(it, current.canRead(), emptyMap<String, String>()) }
                    ?: Triple(emptyList<File>(), false, emptyMap())
            }
        }
        directories = result.first
        readable = result.second
        volumeNames = result.third
        listState.scrollToItem(0)
    }
    LaunchedEffect(isTv, folder, directories) {
        if (isTv && directories.isNotEmpty()) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { firstFolderFocus.requestFocus() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = if (isTv) Modifier.widthIn(max = 720.dp).fillMaxWidth(0.9f) else Modifier,
        properties = DialogProperties(usePlatformDefaultWidth = !isTv),
        title = { Text(AppLanguage.text(R.string.tv_local_folder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(folder?.path ?: AppLanguage.text(R.string.tv_storage_volumes))
                FocusableButton(text = AppLanguage.text(R.string.tv_parent_folder), enabled = folder != null,
                    onClick = {
                        folder = folder?.parentFile?.takeIf {
                            it.path.startsWith("/storage/") && it.path != "/storage/emulated"
                        }
                    })
                LazyColumn(Modifier.height(if (isTv) 200.dp else 240.dp).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(directories, key = { it.path }) { child ->
                        FocusableButton(text = volumeNames[child.path] ?: child.name,
                            modifier = Modifier.fillMaxWidth().then(if (child == directories.first())
                                Modifier.focusRequester(firstFolderFocus) else Modifier),
                            onClick = { readable = false; folder = child })
                    }
                }
                if (folder != null && !readable) Text(AppLanguage.text(R.string.tv_folder_unreadable))
            }
        },
        confirmButton = {
            FocusableButton(text = AppLanguage.text(R.string.tv_use_folder), enabled = readable,
                onClick = { folder?.let(onSelect) })
        },
        dismissButton = { FocusableButton(text = AppLanguage.text(R.string.tv_cancel), onClick = onDismiss) }
    )
}
