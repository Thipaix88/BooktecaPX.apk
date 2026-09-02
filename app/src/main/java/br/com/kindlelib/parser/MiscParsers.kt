package br.com.kindlelib.parser

import br.com.kindlelib.model.ParsedMeta
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object MiscParsers {

    fun parsePdf(open: () -> InputStream?): ParsedMeta {
        val head = open()?.use { runCatching { it.readBytesLimited(128 * 1024) }.getOrNull() } ?: return ParsedMeta()
        val s = String(head, Charsets.ISO_8859_1)
        val m = Regex("""/Title\s*\(([^)]*)\)""").find(s)
        return if (m != null) ParsedMeta(title = m.groupValues[1].trim()) else ParsedMeta()
    }

    fun parseTxt(open: () -> InputStream?): ParsedMeta {
        val head = open()?.use { runCatching { it.readBytesLimited(16 * 1024) }.getOrNull() } ?: return ParsedMeta()
        val s = String(head, Charsets.UTF_8)
        val line = s.lineSequence().firstOrNull { it.isNotBlank() } ?: return ParsedMeta()
        return ParsedMeta(title = line.trim().take(120))
    }

    fun parseFb2(open: () -> InputStream?): ParsedMeta {
        val meta = ParsedMeta()
        val bytes = open()?.use { runCatching { it.readBytesLimited(512 * 1024) }.getOrNull() } ?: return meta
        try {
            val f = XmlPullParserFactory.newInstance()
            f.isNamespaceAware = true
            val x = f.newPullParser()
            x.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var cur: String? = null
            var firstName = ""
            var lastName = ""
            var event = x.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val name = x.name ?: ""
                if (event == XmlPullParser.START_TAG) {
                    when (name) {
                        "book-title" -> cur = "bt"
                        "first-name" -> cur = "fn"
                        "last-name" -> cur = "ln"
                    }
                } else if (event == XmlPullParser.TEXT && cur != null) {
                    val v = (x.text ?: "").trim()
                    when (cur) {
                        "bt" -> if (meta.title.isBlank()) meta.title = v
                        "fn" -> if (firstName.isBlank()) firstName = v
                        "ln" -> if (lastName.isBlank()) lastName = v
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    cur = null
                    if (name == "title-info") break
                }
                event = x.next()
            }
            meta.author = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        } catch (_: Exception) {
        }
        return meta
    }

    fun parseCbz(open: () -> InputStream?, wantCover: Boolean): ParsedMeta {
        val meta = ParsedMeta()
        if (!wantCover) return meta
        try {
            open()?.buffered()?.use { raw ->
                val zin = ZipInputStream(raw)
                var e = zin.nextEntry
                while (e != null) {
                    val n = e.name.lowercase()
                    if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) {
                        meta.coverBytes = runCatching { zin.readBytesLimited(8 * 1024 * 1024) }.getOrNull()
                            ?.takeIf { it.size > 100 }
                        if (meta.coverBytes != null) break
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return meta
    }
}
