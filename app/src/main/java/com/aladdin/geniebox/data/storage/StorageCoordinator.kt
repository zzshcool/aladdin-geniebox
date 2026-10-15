package com.aladdin.geniebox.data.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.aladdin.geniebox.data.repository.RomSourceRepository
import com.aladdin.geniebox.data.scanner.RomScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StorageCoordinator(
    private val context: Context,
    private val romSourceRepository: RomSourceRepository,
    private val romScanner: RomScanner
) {
    companion object {
        private const val TAG = "StorageCoordinator"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.i(TAG, "Storage broadcast received: $action")
            checkSourcesAvailability(autoScanNewSources = (action == Intent.ACTION_MEDIA_MOUNTED))
        }
    }

    fun startListening() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        try {
            context.registerReceiver(mediaReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register media receiver", e)
        }
        // 啟動時立即核對一次來源狀態
        checkSourcesAvailability(autoScanNewSources = false)
    }

    fun stopListening() {
        try {
            context.unregisterReceiver(mediaReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * 前景核對所有來源的可讀狀態，更新在線/離線旗標
     */
    fun checkSourcesAvailability(autoScanNewSources: Boolean = false) {
        scope.launch {
            val sources = romSourceRepository.getAllSources().first()
            for (src in sources) {
                val uri = Uri.parse(src.treeUri)
                val doc = try {
                    DocumentFile.fromTreeUri(context, uri)
                } catch (e: Exception) {
                    null
                }
                val canRead = (doc != null && doc.exists() && doc.canRead())
                if (src.isConnected != canRead) {
                    Log.i(TAG, "Updating source ${src.displayName} connection: $canRead")
                    romSourceRepository.setSourceConnected(src.id, canRead)
                }

                // 重新插入且可讀時，若要求自動掃描則進行增量掃描
                if (canRead && autoScanNewSources) {
                    Log.i(TAG, "Triggering automatic incremental scan for reconnected source: ${src.displayName}")
                    romScanner.scanSource(src.id, uri)
                }
            }
        }
    }
}
