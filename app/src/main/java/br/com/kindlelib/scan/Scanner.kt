package br.com.kindlelib.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import br.com.kindlelib.model.AppSettings
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.BookFormat
import br.com.kindlelib.parser.Parsers
import br.com.kindlelib.parser.cleanTitle
import br.com.kindlelib.parser.fromExt
import br.com.kindlelib.parser.isSupportedExt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Escaneia SOMENTE as pastas que o usuário escolheu explicitamente
 * (settings.extraFolders) + a pasta interna da biblioteca (livros normalizados).
 * Não varre o armazenamento inteiro do aparelho — isso é intencional.
 */
class Scanner(private val context: Context) {

    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val MAX_BOOKS = 4000
    private val MAX_DEPTH = 6

    suspend fun scan(settings: AppSettings, existing: List<Book>): List<Book> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, Book>()
        existing.forEach { out[it.sourceKey()] = it }

        // 1) Pastas escolhidas pelo usuário, acessíveis por caminho direto
        settings.extraFolders.filter { !it.ref.startsWith("content://") }.forEach { ref ->
            if (out.size >= MAX_BOOKS) return@forEach
            val f = File(ref.ref)
            if (f.exists()) walkFile(f, out, 0)
        }

        // 2) Pastas escolhidas pelo usuário via seletor do sistema (SAF)
        settings.extraFolders.filter { it.ref.startsWith("content://") }.forEach { ref ->
            if (out.size >= MAX_BOOKS) return@forEach
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(ref.ref))
                if (tree != null && tree.isDirectory) walkTree(tree, out, 0)
            }
        }

        // 3) Pasta interna da biblioteca (ex: EPUBs normalizados)
        File(context.filesDir, "library").takeIf { it.exists() }?.let { root ->
            if (out.size < MAX_BOOKS) walkFile(root, out, 0)
        }

        out.values.toList()
    }

    private fun walkFile(dir: File, out: MutableMap<String, Book>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_BOOKS) return
        val files = dir.listFiles() ?: return
        files.sortedBy { it.name.lowercase() }.forEach { f ->
            if (out.size >= MAX_BOOKS) return
            val nm = f.name.lowercase()
            if (f.isDirectory) {
                if (nm in IGNORED_DIRS) return@forEach
                walkFile(f, out, depth + 1)
            } else {
                val ext = f.extension.lowercase()
                if (isSupportedExt(ext)) {
                    val key = f.absolutePath
                    val prev = out[key]
                    if (prev != null) {
                        prev.fileSize = f.length()
                        prev.modifiedAt = f.lastModified()
                    } else {
                        val b = Book(
                            id = UUID.randomUUID().toString(),
                            title = cleanTitle(f.name),
                            author = "",
                            format = BookFormat.fromExt(ext),
                            fileName = f.name,
                            sourcePath = f.absolutePath,
                            sourceUri = "",
                            fileSize = f.length(),
                            modifiedAt = f.lastModified()
                        )
                        enrich(b)
                        out[key] = b
                    }
                }
            }
        }
    }

    private fun walkTree(dir: DocumentFile, out: MutableMap<String, Book>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_BOOKS) return
        val children = dir.listFiles().sortedBy { it.name?.lowercase() ?: "" }
        children.forEach { doc ->
            if (out.size >= MAX_BOOKS) return
            if (doc.isDirectory) {
                walkTree(doc, out, depth + 1)
            } else {
                val name = doc.name ?: return@forEach
                val ext = name.substringAfterLast('.', "").lowercase()
                if (isSupportedExt(ext)) {
                    val key = doc.uri.toString()
                    val prev = out[key]
                    if (prev != null) {
                        prev.fileSize = doc.length()
                        prev.modifiedAt = doc.lastModified()
                    } else {
                        val b = Book(
                            id = UUID.randomUUID().toString(),
                            title = cleanTitle(name),
                            author = "",
                            format = BookFormat.fromExt(ext),
                            fileName = name,
                            sourcePath = "",
                            sourceUri = key,
                            fileSize = doc.length(),
                            modifiedAt = doc.lastModified()
                        )
                        enrich(b)
                        out[key] = b
                    }
                }
            }
        }
    }

    private fun enrich(b: Book) {
        runCatching {
            val m = Parsers.parse(b, context, wantCover = true)
            if (m.title.isNotBlank()) b.title = m.title
            if (m.author.isNotBlank()) b.author = m.author
            if (m.series.isNotBlank()) b.series = m.series
            if (m.seriesIndex.isNotBlank()) b.seriesIndex = m.seriesIndex
            if (m.publisher.isNotBlank()) b.publisher = m.publisher
            if (m.language.isNotBlank()) b.language = m.language
            if (m.description.isNotBlank()) b.synopsis = m.description
            if (m.genre.isNotBlank()) b.genre = m.genre
            b.hasDrm = m.hasDrm
            if (m.coverBytes != null && b.coverPath == null) {
                val f = File(coversDir, "cover_${b.id}.jpg")
                f.writeBytes(m.coverBytes!!)
                b.coverPath = f.absolutePath
            }
        }
    }

    companion object {
        private val IGNORED_DIRS = setOf("android", "data", "obb", "cache", ".android", ".thumbnails")
    }
}
