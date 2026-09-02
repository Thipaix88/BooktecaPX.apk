package br.com.kindlelib.model

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

enum class BookFormat { EPUB, MOBI, AZW3, PDF, FB2, TXT, CBZ, CBR, OTHER;
    companion object
}

enum class ReadingStatus(val label: String) { NAO_LIDO("Não lido"), LENDO("Lendo"), LIDO("Lido") }

enum class SortBy(val label: String) { RECENTES("Mais recentes"), TITULO("Título"), AUTOR("Autor"), TAMANHO("Tamanho") }

data class Book(
    val id: String,
    var title: String,
    var author: String,
    var format: BookFormat,
    val fileName: String,
    val sourcePath: String,
    val sourceUri: String,
    var fileSize: Long = 0L,
    var addedAt: Long = System.currentTimeMillis(),
    var modifiedAt: Long = 0L,
    var coverPath: String? = null,
    var synopsis: String = "",
    var series: String = "",
    var seriesIndex: String = "",
    var publisher: String = "",
    var language: String = "",
    var status: ReadingStatus = ReadingStatus.NAO_LIDO,
    var tags: List<String> = emptyList(),
    var collection: String = "",
    var metadataFetched: Boolean = false,
    var hasDrm: Boolean = false
) {
    fun sourceKey(): String = if (sourceUri.startsWith("content://")) sourceUri else sourcePath
    fun displaySize(): String = if (fileSize <= 0) "" else formatSize(fileSize)

    /** Abre o arquivo do livro para leitura, seja via SAF (content://) ou caminho direto em disco. */
    fun open(context: Context): InputStream? = runCatching {
        if (sourceUri.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(sourceUri))
        } else {
            val f = File(sourcePath)
            if (f.exists()) f.inputStream() else null
        }
    }.getOrNull()
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

data class ExtraFolder(val name: String, val ref: String)

data class AppSettings(
    var extraFolders: List<ExtraFolder> = emptyList(),
    var autoFetchMeta: Boolean = true,
    var autoOpenKindle: Boolean = true,
    var kindleFolderUri: String = ""
)

data class CollectionInfo(val name: String, val count: Int)

data class KindleItem(
    val name: String,
    val uri: Uri,
    val size: Long,
    val modified: Long
)

data class ParsedMeta(
    var title: String = "",
    var author: String = "",
    var language: String = "",
    var publisher: String = "",
    var description: String = "",
    var series: String = "",
    var seriesIndex: String = "",
    var coverBytes: ByteArray? = null,
    var coverHref: String = "",
    var coverId: String = "",
    var hasDrm: Boolean = false,
    var isKF8: Boolean = false
)

sealed class Screen {
    object Library : Screen()
    data class Detail(val id: String) : Screen()
    object Kindle : Screen()
    object Settings : Screen()
}
