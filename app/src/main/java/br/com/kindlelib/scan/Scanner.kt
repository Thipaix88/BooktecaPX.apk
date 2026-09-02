package br.com.kindlelib.scan

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import br.com.kindlelib.model.AppSettings
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.BookFormat
import br.com.kindlelib.parser.Parsers
import br.com.kindlelib.parser.cleanTitle
import br.com.kindlelib.parser.isSupportedExt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class Scanner(private val context: Context) {

    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val MAX_BOOKS = 4000
    private val MAX_DEPTH = 5

    suspend fun scan(settings: AppSettings, existing: List<Book>): List<Book> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, Book>()
        existing.forEach { out[it.sourceKey()] = it }

        // 1) Pastas acessíveis por caminho (Downloads + pastas adicionadas por caminho)
        val pathRoots = mutableListOf<File>()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads.exists()) pathRoots += downloads
        settings.extraFolders.filter { !it.ref.startsWith("content://") }.forEach { ref ->
            val f = File(ref.ref)
            if (f.exists()) pathRoots += f
        }
        for (root in pathRoots) {
            if (out.size >= MAX_BOOKS) break
            walkFile(root, out, 0)
        }

        // 2) Pastas SAF (content://) caso o acesso por caminho esteja limitado
        settings.extraFolders.filter { it.ref.startsWith("content://") }.forEach { ref ->
            if (out.size >= MAX_BOOKS) return@forEach
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(ref.ref))
                if (tree != null && tree.isDirectory) walkTree(tree, out, 0)
            }
        }

        // 3) MediaStore (API 29+) — complementa livros em Downloads não vistos por caminho
        queryMediaStore(out)

        // 4) Pasta da própria biblioteca (importados)
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
        if (depth > 4 || out.size >= MAX_BOOKS) return
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

    private fun queryMediaStore(out: MutableMap<String, Book>) {
        if (android.os.Build.VERSION.SDK_INT < 29) return
        runCatching {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                null, null, null
            )?.use { c ->
                val iName = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val iData = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val iMod = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
                    if (out.size >= MAX_BOOKS) break
                    val name = c.getString(iName) ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!isSupportedExt(ext)) continue
                    val data = c.getString(iData) ?: continue
                    val f = File(data)
                    if (!f.exists()) continue
                    if (out.containsKey(f.absolutePath)) continue
                    val b = Book(
                        id = UUID.randomUUID().toString(),
                        title = cleanTitle(name),
                        author = "",
                        format = BookFormat.fromExt(ext),
                        fileName = name,
                        sourcePath = f.absolutePath,
                        sourceUri = "",
                        fileSize = c.getLong(iSize),
                        modifiedAt = c.getLong(iMod) * 1000L
                    )
                    enrich(b)
                    out[f.absolutePath] = b
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
