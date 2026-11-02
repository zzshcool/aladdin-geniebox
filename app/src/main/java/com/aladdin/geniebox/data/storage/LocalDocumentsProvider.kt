package com.aladdin.geniebox.data.storage

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Read-only document URIs for folders explicitly selected in the local TV browser. */
class LocalDocumentsProvider : DocumentsProvider() {
    companion object {
        private const val PREFS = "local_rom_folders"
        private const val ROOTS = "roots"
        private val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )

        fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        fun selectFolder(context: Context, folder: File): Uri {
            val file = folder.canonicalFile
            require(hasAccess(context) && file.isDirectory && file.canRead())
            require(file.path.startsWith("/storage/"))
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val roots = prefs.getStringSet(ROOTS, emptySet()).orEmpty().toMutableSet()
            roots.add(file.path)
            check(prefs.edit().putStringSet(ROOTS, roots).commit())
            val uri = DocumentsContract.buildTreeDocumentUri("${context.packageName}.localdocuments", file.path)
            context.grantUriPermission(context.packageName, uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return uri
        }
    }

    override fun onCreate() = true

    private fun resolve(documentId: String): File {
        val ctx = context ?: throw FileNotFoundException()
        if (!hasAccess(ctx)) throw FileNotFoundException("Storage permission required")
        val file = File(documentId).canonicalFile
        val roots = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(ROOTS, emptySet()).orEmpty()
        if (roots.none { file.path == it || file.path.startsWith("$it/") }) {
            throw FileNotFoundException("Folder was not selected")
        }
        if (!file.exists() || !file.canRead()) throw FileNotFoundException(documentId)
        return file
    }

    private fun MatrixCursor.addFile(file: File) {
        val row = newRow()
        columnNames.forEach { column ->
            row.add(when (column) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> file.canonicalPath
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> file.name
                DocumentsContract.Document.COLUMN_MIME_TYPE -> if (file.isDirectory)
                    DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                DocumentsContract.Document.COLUMN_SIZE -> file.length()
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                DocumentsContract.Document.COLUMN_FLAGS -> 0
                else -> null
            })
        }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: columns).apply { addFile(resolve(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val parent = resolve(parentDocumentId)
        val children = parent.listFiles() ?: throw FileNotFoundException(parentDocumentId)
        return MatrixCursor(projection ?: columns).apply {
            children.forEach { file ->
                try { addFile(resolve(file.path)) } catch (_: FileNotFoundException) { }
            }
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        resolve(documentId).path.startsWith(resolve(parentDocumentId).path + "/")

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        signal?.throwIfCanceled()
        return ParcelFileDescriptor.open(resolve(documentId), ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
