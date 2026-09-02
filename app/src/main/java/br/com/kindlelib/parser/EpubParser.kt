package br.com.kindlelib.parser

import br.com.kindlelib.model.ParsedMeta
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object EpubParser {

    private const val CONTAINER = "META-INF/container.xml"

    fun parse(open: () -> InputStream?, wantCover: Boolean): ParsedMeta {
        val meta = ParsedMeta()
        var containerBytes: ByteArray? = null
        val opfCandidates = mutableListOf<Pair<String, ByteArray>>()
        open()?.buffered()?.use { raw ->
            val zin = ZipInputStream(raw)
            var e = zin.nextEntry
            while (e != null) {
                val name = e.name
                when {
                    name.equals(CONTAINER, ignoreCase = true) ->
                        containerBytes = runCatching { zin.readBytesLimited(64 * 1024) }.getOrNull()
                    name.endsWith(".opf", ignoreCase = true) && opfCandidates.size < 3 ->
                        runCatching { opfCandidates += name to zin.readBytesLimited(400 * 1024) }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        val opfPath = containerBytes?.let { regexFullPath(it) }
        val opf = opfCandidates.firstOrNull { it.first.equals(opfPath, ignoreCase = true) }
            ?: opfCandidates.firstOrNull()
            ?: return meta
        parseOpf(opf.second, meta)
        if (wantCover && meta.coverBytes == null && meta.coverHref.isNotBlank()) {
            val target = resolvePath(opf.first, meta.coverHref)
            open()?.buffered()?.use { raw ->
                val zin = ZipInputStream(raw)
                var e = zin.nextEntry
                while (e != null) {
                    if (normalize(e.name) == normalize(target)) {
                        meta.coverBytes = runCatching { zin.readBytesLimited(8 * 1024 * 1024) }.getOrNull()
                            ?.takeIf { it.size > 100 }
                        break
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            }
        }
        return meta
    }

    private fun regexFullPath(container: ByteArray): String? =
        Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(String(container, Charsets.UTF_8))?.groupValues?.get(1)

    private fun normalize(s: String): String = s.replace('\\', '/').lowercase().trimStart('/')

    private fun resolvePath(opfPath: String, href: String): String {
        val base = opfPath.substringBeforeLast('/', "").ifEmpty { "" }
        val parts = mutableListOf<String>()
        if (base.isNotEmpty()) parts.addAll(base.split('/'))
        href.split('/').forEach { seg ->
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun parseOpf(bytes: ByteArray, meta: ParsedMeta) {
        try {
            val f = XmlPullParserFactory.newInstance()
            f.isNamespaceAware = true
            val x = f.newPullParser()
            x.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var inMeta = false
            var inManifest = false
            var cur: String? = null
            val sb = StringBuilder()
            var savedTitle = false

            fun flush() {
                val v = sb.toString().trim()
                if (v.isEmpty()) return
                when (cur) {
                    "title" -> if (!savedTitle) { meta.title = v; savedTitle = true }
                    "creator" -> if (meta.author.isBlank()) meta.author = v
                    "language" -> if (meta.language.isBlank()) meta.language = v
                    "publisher" -> if (meta.publisher.isBlank()) meta.publisher = v
                    "description" -> if (meta.description.isBlank()) meta.description = v
                }
                sb.setLength(0)
            }

            var event = x.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val name = x.name ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> when {
                        name == "metadata" -> inMeta = true
                        name == "manifest" -> inManifest = true
                        inMeta && name == "meta" -> {
                            val n = x.getAttributeValue(null, "name") ?: ""
                            val c = x.getAttributeValue(null, "content") ?: ""
                            val p = x.getAttributeValue(null, "property") ?: ""
                            when {
                                n == "calibre:series" || p == "belongs-to-collection" ->
                                    if (meta.series.isBlank()) meta.series = c
                                n == "calibre:series_index" ->
                                    if (meta.seriesIndex.isBlank()) meta.seriesIndex = c
                                n == "cover" -> meta.coverId = c
                            }
                        }
                        (inMeta || inManifest) && name == "item" -> {
                            if (meta.coverHref.isBlank()) {
                                val id = x.getAttributeValue(null, "id") ?: ""
                                val href = x.getAttributeValue(null, "href") ?: ""
                                val props = x.getAttributeValue(null, "properties") ?: ""
                                if (meta.coverId.isNotBlank() && id == meta.coverId && href.isNotBlank()) meta.coverHref = href
                                if (props.contains("cover-image", ignoreCase = true) && href.isNotBlank()) meta.coverHref = href
                            }
                        }
                        inMeta && (name == "title" || name == "creator" || name == "language" ||
                                name == "publisher" || name == "description") -> {
                            flush()
                            cur = name
                            sb.setLength(0)
                        }
                    }
                    XmlPullParser.TEXT -> if (cur != null) sb.append(x.text ?: "")
                    XmlPullParser.END_TAG -> when (name) {
                        "title", "creator", "language", "publisher", "description" -> flush()
                        "meta", "item" -> {}
                        "metadata" -> inMeta = false
                        "manifest" -> inManifest = false
                    }
                }
                event = x.next()
            }
        } catch (_: Exception) {
        }
        meta.title = meta.title.trim()
        meta.author = meta.author.trim()
    }
}
