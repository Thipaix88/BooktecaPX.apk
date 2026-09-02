package br.com.kindlelib.meta

import android.content.Context
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.ParsedMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MetadataFetcher(private val context: Context) {

    suspend fun fetch(book: Book): ParsedMeta? = withContext(Dispatchers.IO) {
        val baseQuery = listOfNotNull(
            "intitle:${book.title.take(80)}",
            book.author.takeIf { it.isNotBlank() }?.let { "inauthor:$it" }
        ).joinToString("+")
        if (baseQuery.isBlank()) return@withContext null

        // 1) Google Books
        var best = tryGoogle(baseQuery)
        // 2) Open Library como complemento (principalmente capa/sinopse)
        if (best == null || best.coverBytes == null) {
            val ol = tryOpenLibrary(book)
            if (best == null) best = ol
            else if (ol != null) {
                if (best.coverBytes == null) best.coverBytes = ol.coverBytes
                if (best.description.isBlank()) best.description = ol.description
                if (best.series.isBlank()) best.series = ol.series
            }
        }
        best
    }

    private fun tryGoogle(query: String): ParsedMeta? {
        return runCatching {
            val url = "https://www.googleapis.com/books/v1/volumes?q=$query&maxResults=5&printType=books"
            val json = getJson(url) ?: return null
            val items = json.optJSONArray("items") ?: return null
            if (items.length() == 0) return null
            val v = items.getJSONObject(0).optJSONObject("volumeInfo") ?: return null
            val meta = ParsedMeta()
            meta.title = v.optString("title").trim()
            val sub = v.optString("subtitle").trim()
            if (meta.title.isBlank() && sub.isNotBlank()) meta.title = sub
            v.optJSONArray("authors")?.takeIf { it.length() > 0 }?.let { meta.author = it.getString(0) }
            meta.description = v.optString("description").substringBefore("\n\n").take(1200)
            meta.publisher = v.optString("publisher")
            meta.language = v.optString("language")
            v.optJSONObject("seriesInfo")?.let { si ->
                meta.series = si.optString("shortSeriesList").ifBlank { si.optString("bookDisplayName") }
            }
            val img = v.optJSONObject("imageLinks")
            img?.optString("thumbnail")?.let { thumb ->
                if (thumb.isNotBlank()) meta.coverBytes = getBytes(thumb, 4 * 1024 * 1024)
            }
            meta
        }.getOrNull()
    }

    private fun tryOpenLibrary(book: Book): ParsedMeta? {
        return runCatching {
            val title = URLEncoder.encode(book.title.take(80), "UTF-8")
            val author = URLEncoder.encode(book.author.take(60), "UTF-8")
            val url = "https://openlibrary.org/search.json?title=$title&author=$author&limit=3&fields=title,author_name,cover_i,first_publish_year"
            val json = getJson(url) ?: return null
            val docsArray = json.optJSONArray("docs")
            val docsObject = json.optJSONObject("docs")
            val first: JSONObject = when {
                docsArray != null && docsArray.length() > 0 -> docsArray.getJSONObject(0)
                docsObject != null -> docsObject
                else -> return null
            }
            val meta = ParsedMeta()
            meta.title = first.optString("title").trim()
            first.optJSONArray("author_name")?.takeIf { it.length() > 0 }?.let { meta.author = it.getString(0) }
            val coverI = first.optInt("cover_i", -1)
            if (coverI > 0) meta.coverBytes = getBytes("https://covers.openlibrary.org/b/id/$coverI-L.jpg", 4 * 1024 * 1024)
            val year = first.optInt("first_publish_year", -1)
            if (year > 0) meta.publisher = year.toString()
            meta
        }.getOrNull()
    }

    private fun getJson(urlStr: String): JSONObject? {
        val bytes = getBytes(urlStr, 2 * 1024 * 1024) ?: return null
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun getBytes(urlStr: String, cap: Int): ByteArray? {
        return runCatching {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "KindleLib/1.0 (Android)")
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) { conn.disconnect(); return null }
            val inp = conn.inputStream
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (total < cap) {
                val n = inp.read(buf, 0, minOf(buf.size, cap - total))
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
            }
            inp.close()
            conn.disconnect()
            out.toByteArray()
        }.getOrNull()
    }
}
