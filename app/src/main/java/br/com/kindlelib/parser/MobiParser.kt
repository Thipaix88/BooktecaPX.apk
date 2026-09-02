package br.com.kindlelib.parser

import br.com.kindlelib.model.ParsedMeta
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.Charset

object MobiParser {

    fun parse(open: () -> InputStream?, sizeHint: Long, wantCover: Boolean): ParsedMeta {
        val meta = ParsedMeta()
        val cap = (if (sizeHint > 0) sizeHint else 0L).coerceAtMost(28L * 1024 * 1024).toInt()
        val bytes = open()?.use { runCatching { it.readBytesLimited(cap) }.getOrNull() } ?: return meta
        if (bytes.size < 160) return meta

        val r0 = record0Offset(bytes) ?: return meta
        if (r0 + 132 > bytes.size) return meta
        if (readIntAt(bytes, r0) != 0x4D4F4249) return meta // "MOBI"

        val headerLen = readIntAt(bytes, r0 + 4)
        val encoding = readIntAt(bytes, r0 + 12)
        val charset = if (encoding == 65001) Charsets.UTF_8 else Charset.forName("windows-1252")
        val fullNameOff = readIntAt(bytes, r0 + 68)
        val fullNameLen = readIntAt(bytes, r0 + 72)
        val exthFlags = readIntAt(bytes, r0 + 112)
        val drmCount = readIntAt(bytes, r0 + 124)
        meta.hasDrm = drmCount > 0

        if (fullNameLen in 1..1024) {
            val abs = r0 + fullNameOff
            if (abs in 0 until bytes.size && abs + fullNameLen <= bytes.size) {
                meta.title = String(bytes, abs, fullNameLen, charset).trim()
            }
        }

        val exthPos = r0 + headerLen
        if (exthFlags and 0x40 != 0 && exthPos + 12 <= bytes.size && readIntAt(bytes, exthPos) == 0x45585448) {
            val count = readIntAt(bytes, exthPos + 8)
            var p = exthPos + 12
            repeat(count.coerceAtMost(120)) {
                if (p + 8 > bytes.size) return@repeat
                val type = readIntAt(bytes, p)
                val len = readIntAt(bytes, p + 4)
                val ds = p + 8
                val dl = len - 8
                if (dl > 0 && ds + dl <= bytes.size && dl <= 4 * 1024 * 1024) {
                    when (type) {
                        100 -> if (meta.author.isBlank()) meta.author = String(bytes, ds, dl, charset).trim()
                        101 -> if (meta.publisher.isBlank()) meta.publisher = String(bytes, ds, dl, charset).trim()
                        103 -> if (meta.description.isBlank()) meta.description = String(bytes, ds, dl, charset).trim()
                        121 -> meta.isKF8 = true
                        131 -> if (wantCover && meta.coverBytes == null && dl >= 4) {
                            val off = readIntAt(bytes, ds)
                            if (off > 0 && off < bytes.size) meta.coverBytes = extractJpeg(bytes, off)
                        }
                        201 -> { val (s, i) = parseSeries(String(bytes, ds, dl, charset)); if (meta.series.isBlank()) meta.series = s; if (meta.seriesIndex.isBlank() && i != null) meta.seriesIndex = i }
                        202 -> if (meta.seriesIndex.isBlank() && dl <= 32) meta.seriesIndex = String(bytes, ds, dl, charset).trim()
                    }
                }
                p += len
            }
        }
        if (meta.title.isBlank()) meta.title = ""
        return meta
    }

    private fun record0Offset(b: ByteArray): Int? {
        if (b.size < 78) return null
        val d = DataInputStream(ByteArrayInputStream(b))
        val name = ByteArray(32)
        runCatching {
            d.readFully(name)
            d.skipBytes(28) // attributes(2) version(2) ctime(4) mtime(4) btime(4) modnum(4) appinfo(4) sortinfo(4)
            d.readInt()   // 60 type
            d.readInt()   // 64 creator
            d.skipBytes(8) // 68 uid(4) 72 nextrecord(4)
            val numRecords = d.readShort().toInt() // 76
            if (numRecords <= 0 || numRecords > 4096) return null
            val rec0 = d.readInt() // offset do registro 0 (início da lista: posição 78)
            return rec0
        }.getOrNull()
        return null
    }

    private fun readIntAt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
                ((b[off + 1].toInt() and 0xFF) shl 16) or
                ((b[off + 2].toInt() and 0xFF) shl 8) or
                (b[off + 3].toInt() and 0xFF)

    private fun parseSeries(s: String): Pair<String, String?> {
        val m = Regex("^(.*?)\\s*\\((\\d+)\\)\\s*$").find(s.trim())
        return if (m != null) m.groupValues[1].trim() to m.groupValues[2] else s.trim() to null
    }

    private fun extractJpeg(b: ByteArray, off: Int): ByteArray? {
        if (off + 2 > b.size) return null
        if ((b[off].toInt() and 0xFF) != 0xFF || (b[off + 1].toInt() and 0xFF) != 0xD8) return null
        val end = minOf(b.size, off + 6 * 1024 * 1024)
        var i = off + 2
        while (i < end - 1) {
            if ((b[i].toInt() and 0xFF) == 0xFF && (b[i + 1].toInt() and 0xFF) == 0xD9) {
                return b.copyOfRange(off, i + 2)
            }
            i++
        }
        return b.copyOfRange(off, end)
    }
}
