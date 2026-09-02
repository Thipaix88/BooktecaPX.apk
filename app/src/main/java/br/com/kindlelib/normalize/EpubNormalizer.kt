package br.com.kindlelib.normalize

import android.content.Context
import br.com.kindlelib.model.Book
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Normalização de EPUB: reembala o arquivo no layout canônico exigido pelo Kindle
 * (mimetype primeiro, sem compressão, zíper sem diretórios extras) e garante
 * entrada "mimetype" presente. Não altera o arquivo original — gera uma cópia
 * na pasta interna da biblioteca, sempre legível pelo app.
 */
object EpubNormalizer {

    fun normalize(book: Book, context: Context): File? {
        if (book.format != br.com.kindlelib.model.BookFormat.EPUB) return null
        val input = book.open(context) ?: return null
        val dir = File(context.filesDir, "library").apply { mkdirs() }
        val libDir = File(context.filesDir, "library")
        val out = File(libDir, "normalizado_${book.id}.epub")
        return runCatching {
            input.use { raw ->
                ZipOutputStream(out.outputStream().buffered()).use { zos ->
                    // 1) mimetype
                    zos.putNextEntry(ZipEntry("mimetype").apply { method = ZipEntry.STORED; size = 20; compressedSize = 20; crc = 0x2BACAF21 })
                    zos.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
                    zos.closeEntry()
                    // 2) demais entradas
                    val zin = ZipInputStream(raw.buffered())
                    var e = zin.nextEntry
                    while (e != null) {
                        if (!e.isDirectory && !e.name.equals("mimetype", ignoreCase = true)) {
                            zos.putNextEntry(ZipEntry(e.name))
                            val buf = ByteArray(64 * 1024)
                            var n = zin.read(buf)
                            while (n >= 0) {
                                if (n > 0) zos.write(buf, 0, n)
                                n = zin.read(buf)
                            }
                            zos.closeEntry()
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                }
            }
            if (out.length() > 0) out else null
        }.getOrNull()
    }
}
