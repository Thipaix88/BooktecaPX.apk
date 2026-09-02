package br.com.kindlelib.data

import android.content.Context
import android.net.Uri
import br.com.kindlelib.model.AppSettings
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.BookFormat
import br.com.kindlelib.model.ExtraFolder
import br.com.kindlelib.model.ReadingStatus
import org.json.JSONArray
import org.json.JSONObject

class LibraryStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("kindlelib", Context.MODE_PRIVATE)

    fun loadBooks(): List<Book> {
        val raw = prefs.getString("books_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> bookFromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun saveBooks(books: List<Book>) {
        val arr = JSONArray()
        books.forEach { arr.put(bookToJson(it)) }
        prefs.edit().putString("books_json", arr.toString()).apply()
    }

    fun loadSettings(): AppSettings {
        val raw = prefs.getString("settings_json", null) ?: return AppSettings()
        return runCatching {
            val o = JSONObject(raw)
            val folders = JSONArray(o.optString("extraFolders", "[]"))
            val list = (0 until folders.length()).map { i ->
                val fo = folders.getJSONObject(i)
                ExtraFolder(fo.optString("name"), fo.optString("ref"))
            }
            AppSettings(
                extraFolders = list,
                autoFetchMeta = o.optBoolean("autoFetchMeta", true),
                autoOpenKindle = o.optBoolean("autoOpenKindle", true),
                kindleFolderUri = o.optString("kindleFolderUri", "")
            )
        }.getOrDefault(AppSettings())
    }

    fun saveSettings(s: AppSettings) {
        val o = JSONObject()
        val folders = JSONArray()
        s.extraFolders.forEach { folders.put(JSONObject().put("name", it.name).put("ref", it.ref)) }
        o.put("extraFolders", folders)
        o.put("autoFetchMeta", s.autoFetchMeta)
        o.put("autoOpenKindle", s.autoOpenKindle)
        o.put("kindleFolderUri", s.kindleFolderUri)
        prefs.edit().putString("settings_json", o.toString()).apply()
    }

    // --- serialização de Book ---

    private fun bookToJson(b: Book): JSONObject = JSONObject().apply {
        put("id", b.id)
        put("title", b.title)
        put("author", b.author)
        put("format", b.format.name)
        put("fileName", b.fileName)
        put("sourcePath", b.sourcePath)
        put("sourceUri", b.sourceUri)
        put("fileSize", b.fileSize)
        put("addedAt", b.addedAt)
        put("modifiedAt", b.modifiedAt)
        put("coverPath", b.coverPath ?: "")
        put("synopsis", b.synopsis)
        put("series", b.series)
        put("seriesIndex", b.seriesIndex)
        put("publisher", b.publisher)
        put("language", b.language)
        put("status", b.status.name)
        put("tags", JSONArray(b.tags))
        put("collection", b.collection)
        put("metadataFetched", b.metadataFetched)
        put("hasDrm", b.hasDrm)
    }

    private fun bookFromJson(o: JSONObject): Book = Book(
        id = o.getString("id"),
        title = o.optString("title"),
        author = o.optString("author"),
        format = runCatching { BookFormat.valueOf(o.optString("format")) }.getOrDefault(BookFormat.OTHER),
        fileName = o.optString("fileName"),
        sourcePath = o.optString("sourcePath"),
        sourceUri = o.optString("sourceUri"),
        fileSize = o.optLong("fileSize"),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
        modifiedAt = o.optLong("modifiedAt"),
        coverPath = o.optString("coverPath").ifBlank { null },
        synopsis = o.optString("synopsis"),
        series = o.optString("series"),
        seriesIndex = o.optString("seriesIndex"),
        publisher = o.optString("publisher"),
        language = o.optString("language"),
        status = runCatching { ReadingStatus.valueOf(o.optString("status")) }.getOrDefault(ReadingStatus.NAO_LIDO),
        tags = runCatching { (0 until o.optJSONArray("tags")!!.length()).map { i -> o.optJSONArray("tags").getString(i) } }
            .getOrDefault(emptyList()),
        collection = o.optString("collection"),
        metadataFetched = o.optBoolean("metadataFetched"),
        hasDrm = o.optBoolean("hasDrm")
    )

    companion object {
        fun uriOf(s: String): Uri? = if (s.isNotBlank()) runCatching { Uri.parse(s) }.getOrNull() else null
    }
}
