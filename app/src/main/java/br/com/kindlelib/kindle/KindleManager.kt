package br.com.kindlelib.kindle

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.KindleItem
import java.io.File

object KindleProtocol {
    val KINDLE_EXTS = listOf("azw", "azw3", "azw4", "mobi", "prc", "epub", "pdf", "txt", "kfx", "azw1", "tpz")
}

class KindleManager(private val app: Application) {

    fun hasFolder(uri: Uri?): Boolean = uri != null

    fun persistTree(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun listBooks(uri: Uri): List<KindleItem> {
        val tree = DocumentFile.fromTreeUri(app, uri) ?: return emptyList()
        return tree.listFiles()
            .filter { !it.isDirectory }
            .filter { doc ->
                val name = doc.name ?: return@filter false
                if (name.startsWith(".")) return@filter false
                KindleProtocol.KINDLE_EXTS.any { name.endsWith(".$it", ignoreCase = true) }
            }
            .map { KindleItem(it.name ?: "", it.uri, it.length(), it.lastModified()) }
            .sortedBy { it.name.lowercase() }
    }

    fun sendToKindle(book: Book, treeUri: Uri): Boolean {
        return runCatching {
            val tree = DocumentFile.fromTreeUri(app, treeUri) ?: return false
            val outDoc = tree.findFile(book.fileName) ?: tree.createFile("application/octet-stream", book.fileName)
                ?: return false
            val input = book.open(app) ?: return false
            val output = app.contentResolver.openOutputStream(outDoc.uri, "w") ?: return false
            input.use { i ->
                output.use { o ->
                    val buf = ByteArray(64 * 1024)
                    var n = i.read(buf)
                    while (n >= 0) {
                        if (n > 0) o.write(buf, 0, n)
                        n = i.read(buf)
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    fun deleteFromKindle(item: KindleItem): Boolean {
        return runCatching {
            DocumentFile.fromSingleUri(app, item.uri)?.delete() ?: false
        }.getOrDefault(false)
    }

    fun probeUsb(context: Context): String? {
        // Best-effort: enumera dispositivos USB (host) e volumes removíveis montados (MTP)
        val names = mutableListOf<String>()
        runCatching {
            val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
            usb.deviceList.values.forEach {
                val n = it.productName ?: it.manufacturerName ?: it.deviceName
                names += n ?: "Dispositivo USB"
            }
        }
        var mtpName: String? = null
        runCatching {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.storageVolumes.forEach { vol ->
                if (vol.isRemovable && vol.state == Environment.MEDIA_MOUNTED) {
                    mtpName = runCatching { vol.getDescription(context) }.getOrNull() ?: "Volume USB"
                }
            }
        }
        return when {
            mtpName != null -> mtpName
            names.isNotEmpty() -> names.joinToString(", ")
            else -> null
        }
    }

    /** Busca um diretório "documents" dentro da árvore SAF do Kindle (se o usuário apontou a raiz). */
    fun findDocumentsDir(uri: Uri): Uri? {
        val tree = DocumentFile.fromTreeUri(app, uri) ?: return null
        val direct = tree.listFiles().firstOrNull {
            it.isDirectory && it.name.equals("documents", ignoreCase = true)
        }
        if (direct != null) return direct.uri
        // alguns Kindles expõem a raiz direto como documents
        if (tree.name.equals("documents", ignoreCase = true)) return tree.uri
        return null
    }

    fun openBookFile(context: Context, book: Book) {
        runCatching {
            val uri = if (book.sourceUri.startsWith("content://")) Uri.parse(book.sourceUri)
            else Uri.fromFile(File(book.sourcePath))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, book.mimeType())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun Book.mimeType(): String = when (format) {
        br.com.kindlelib.model.BookFormat.EPUB -> "application/epub+zip"
        br.com.kindlelib.model.BookFormat.PDF -> "application/pdf"
        br.com.kindlelib.model.BookFormat.MOBI, br.com.kindlelib.model.BookFormat.AZW3 -> "application/x-mobipocket-ebook"
        br.com.kindlelib.model.BookFormat.TXT -> "text/plain"
        br.com.kindlelib.model.BookFormat.FB2 -> "application/x-fictionbook+xml"
        br.com.kindlelib.model.BookFormat.CBZ -> "application/x-cbz"
        br.com.kindlelib.model.BookFormat.CBR -> "application/x-cbr"
        else -> "application/octet-stream"
    }
}
