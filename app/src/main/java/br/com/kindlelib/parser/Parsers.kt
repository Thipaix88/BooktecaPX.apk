package br.com.kindlelib.parser

import br.com.kindlelib.model.BookFormat

val SUPPORTED_EXTS = listOf("epub", "mobi", "azw3", "azw", "prc", "pdf", "fb2", "txt", "cbz", "cbr")

fun BookFormat.Companion.fromExt(ext: String): BookFormat = when (ext.lowercase()) {
    "epub" -> BookFormat.EPUB
    "mobi", "prc" -> BookFormat.MOBI
    "azw3", "azw" -> BookFormat.AZW3
    "pdf" -> BookFormat.PDF
    "fb2" -> BookFormat.FB2
    "txt" -> BookFormat.TXT
    "cbz" -> BookFormat.CBZ
    "cbr" -> BookFormat.CBR
    else -> BookFormat.OTHER
}

fun cleanTitle(fileName: String): String =
    fileName.substringBeforeLast('.').replace('_', ' ').replace('.', ' ').trim().replace(Regex("\\s+"), " ")

fun isSupportedExt(ext: String): Boolean = ext.lowercase() in SUPPORTED_EXTS

fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - total))
        if (n <= 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
