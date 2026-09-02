package br.com.kindlelib.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.kindlelib.data.LibraryStore
import br.com.kindlelib.kindle.KindleManager
import br.com.kindlelib.meta.MetadataFetcher
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.BookFormat
import br.com.kindlelib.model.CollectionInfo
import br.com.kindlelib.model.ExtraFolder
import br.com.kindlelib.model.KindleItem
import br.com.kindlelib.model.ReadingStatus
import br.com.kindlelib.model.Screen
import br.com.kindlelib.model.SortBy
import br.com.kindlelib.normalize.EpubNormalizer
import br.com.kindlelib.scan.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LibraryStore(app)
    private val scanner = Scanner(app)
    private val fetcher = MetadataFetcher(app)
    private val kindle = KindleManager(app)
    private val coversDir = File(app.filesDir, "covers").apply { mkdirs() }

    val books = MutableStateFlow(store.loadBooks())
    val collections = MutableStateFlow<List<CollectionInfo>>(emptyList())
    val settings = MutableStateFlow(store.loadSettings())

    val query = MutableStateFlow("")
    val authorFilter = MutableStateFlow<String?>(null)
    val formatFilter = MutableStateFlow<BookFormat?>(null)
    val statusFilter = MutableStateFlow<ReadingStatus?>(null)
    val transferredFilter = MutableStateFlow<Boolean?>(null)
    val collectionFilter = MutableStateFlow("")
    val sortBy = MutableStateFlow(SortBy.RECENTES)

    val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    private var didInitialScan = false

    val scanning = MutableStateFlow(false)
    val metaBusy = MutableStateFlow(false)
    val usbDevice = MutableStateFlow<String?>(null)
    val kindleUri = MutableStateFlow<Uri?>(LibraryStore.uriOf(store.loadSettings().kindleFolderUri))
    val kindleBooks = MutableStateFlow<List<KindleItem>>(emptyList())
    val transferProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val transferDone = MutableStateFlow<String?>(null)

    val screen = MutableStateFlow<Screen>(
        if (store.loadSettings().extraFolders.isEmpty()) Screen.Onboarding else Screen.Library
    )
    val shownBook = MutableStateFlow<Book?>(null)
    val message = MutableStateFlow<String?>(null)

    init {
        refreshCollections()
    }

    // ---------------- navegação ----------------

    fun openBook(id: String) {
        shownBook.value = books.value.firstOrNull { it.id == id }
        screen.value = Screen.Detail(id)
    }
    fun closeDetail() { screen.value = Screen.Library }
    fun openKindle() { screen.value = Screen.Kindle }
    fun openSettings() { screen.value = Screen.Settings }
    fun goLibrary() { screen.value = Screen.Library }

    // ---------------- biblioteca ----------------

    fun onStart(context: Context) {
        refreshUsb()
        if (!didInitialScan && hasStorageAccess(context) && settings.value.extraFolders.isNotEmpty()) {
            didInitialScan = true
            scanLibrary()
        }
    }

    fun finishOnboarding(uri: Uri) {
        addFolder(uri)
        screen.value = Screen.Library
    }

    fun scanLibrary() {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            val found = scanner.scan(settings.value, books.value)
            books.value = found
            store.saveBooks(found)
            refreshCollections()
            scanning.value = false
            message.value = "Biblioteca atualizada: ${found.size} livros"
            if (settings.value.autoFetchMeta) fetchMissing()
        }
    }

    private fun fetchMissing() {
        val pending = books.value.filter { !it.metadataFetched }.take(40)
        if (pending.isEmpty()) return
        viewModelScope.launch {
            pending.forEach { b ->
                if (!b.metadataFetched) fetchMetadata(b, force = false, silent = true)
                delay(250)
            }
        }
    }

    fun filteredBooks(): List<Book> {
        var list = books.value
        val q = query.value.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.author.lowercase().contains(q) ||
                        it.series.lowercase().contains(q) ||
                        it.tags.any { t -> t.lowercase().contains(q) }
            }
        }
        authorFilter.value?.let { a -> list = list.filter { it.author.equals(a, ignoreCase = true) } }
        formatFilter.value?.let { f -> list = list.filter { it.format == f } }
        statusFilter.value?.let { s -> list = list.filter { it.status == s } }
        transferredFilter.value?.let { t -> list = list.filter { it.transferred == t } }
        if (collectionFilter.value.isNotEmpty()) {
            val c = collectionFilter.value
            list = list.filter { it.collection.equals(c, ignoreCase = true) }
        }
        return when (sortBy.value) {
            SortBy.RECENTES -> list.sortedByDescending { it.addedAt }
            SortBy.TITULO -> list.sortedBy { it.title.lowercase() }
            SortBy.AUTOR -> list.sortedBy { it.author.lowercase() }
            SortBy.TAMANHO -> list.sortedByDescending { it.fileSize }
        }
    }

    fun setQuery(q: String) { query.value = q }
    fun setSort(s: SortBy) { sortBy.value = s }
    fun setStatusFilter(s: ReadingStatus?) { statusFilter.value = s }
    fun setTransferredFilter(t: Boolean?) { transferredFilter.value = t }
    fun setFormatFilter(f: BookFormat?) { formatFilter.value = f }
    fun setAuthorFilter(a: String?) { authorFilter.value = a }
    fun setCollectionFilter(c: String) { collectionFilter.value = c }

    fun updateBook(b: Book) {
        val list = books.value.map { if (it.id == b.id) b else it }
        books.value = list
        store.saveBooks(list)
        refreshCollections()
    }

    fun deleteBook(b: Book) {
        val list = books.value.filter { it.id != b.id }
        books.value = list
        store.saveBooks(list)
        b.coverPath?.let { runCatching { File(it).delete() } }
        refreshCollections()
        message.value = "\"${b.title}\" removido da biblioteca (o arquivo original foi mantido)"
    }

    fun setStatus(b: Book, s: ReadingStatus) { updateBook(b.copy(status = s)) }

    // ---------------- seleção múltipla ----------------

    fun toggleSelect(id: String) {
        val cur = selectedIds.value.toMutableSet()
        if (!cur.remove(id)) cur.add(id)
        selectedIds.value = cur
    }

    fun startSelection(id: String) { selectedIds.value = setOf(id) }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun bulkDelete() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        val toRemove = books.value.filter { it.id in ids }
        val list = books.value.filter { it.id !in ids }
        books.value = list
        store.saveBooks(list)
        toRemove.forEach { b -> b.coverPath?.let { runCatching { File(it).delete() } } }
        refreshCollections()
        message.value = "${ids.size} livro(s) removido(s) da biblioteca"
        clearSelection()
    }

    fun bulkMarkTransferred() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        val list = books.value.map { if (it.id in ids) it.copy(transferred = true) else it }
        books.value = list
        store.saveBooks(list)
        message.value = "${ids.size} livro(s) marcado(s) como transferido(s)"
        clearSelection()
    }

    fun bulkSendToKindle() {
        val sel = books.value.filter { it.id in selectedIds.value }
        clearSelection()
        sendToKindle(sel)
    }

    fun setCoverFromUri(b: Book, uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) { message.value = "Não foi possível ler a imagem"; return@launch }
            val f = File(coversDir, "cover_${b.id}.jpg")
            withContext(Dispatchers.IO) { f.writeBytes(bytes) }
            updateBook(b.copy(coverPath = f.absolutePath))
            message.value = "Capa atualizada"
        }
    }

    fun normalizeEpub(b: Book) {
        if (b.format != BookFormat.EPUB) {
            message.value = "Normalização disponível apenas para EPUB"
            return
        }
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { EpubNormalizer.normalize(b, getApplication()) }
            if (out == null) { message.value = "Falha ao normalizar o EPUB"; return@launch }
            val nb = b.copy(
                sourcePath = out.absolutePath,
                sourceUri = "",
                fileName = out.name,
                fileSize = out.length()
            )
            updateBook(nb)
            message.value = "EPUB normalizado (reembalado) e copiado para a biblioteca interna"
        }
    }

    fun fetchMetadata(b: Book, force: Boolean = false, silent: Boolean = false) {
        if (metaBusy.value) return
        viewModelScope.launch {
            metaBusy.value = true
            val res = withContext(Dispatchers.IO) { fetcher.fetch(b) }
            if (res != null && (res.title.isNotBlank() || res.coverBytes != null)) {
                var nb = b
                if (res.title.isNotBlank()) nb = nb.copy(title = res.title)
                if (res.author.isNotBlank()) nb = nb.copy(author = res.author)
                if (res.description.isNotBlank()) nb = nb.copy(synopsis = res.description)
                if (res.series.isNotBlank()) nb = nb.copy(series = res.series, seriesIndex = res.seriesIndex)
                if (res.publisher.isNotBlank() && nb.publisher.isBlank()) nb = nb.copy(publisher = res.publisher)
                if (res.language.isNotBlank() && nb.language.isBlank()) nb = nb.copy(language = res.language)
                if (res.genre.isNotBlank() && nb.genre.isBlank()) nb = nb.copy(genre = res.genre)
                if (res.coverBytes != null) {
                    val f = File(coversDir, "cover_${b.id}.jpg")
                    withContext(Dispatchers.IO) { f.writeBytes(res.coverBytes!!) }
                    nb = nb.copy(coverPath = f.absolutePath)
                }
                nb = nb.copy(metadataFetched = true)
                updateBook(nb)
                if (!silent) message.value = "Metadados atualizados: ${nb.title}"
            } else {
                if (!silent) message.value = "Nenhum metadado encontrado online para este livro"
            }
            metaBusy.value = false
        }
    }

    // ---------------- pastas / configurações ----------------

    fun addFolder(uri: Uri) {
        kindle.persistTree(uri)
        val name = runCatching {
            DocumentFile.fromTreeUri(getApplication(), uri)?.name
        }.getOrNull() ?: "Pasta"
        val list = settings.value.extraFolders.toMutableList()
        if (list.none { it.ref == uri.toString() }) list += ExtraFolder(name, uri.toString())
        settings.value = settings.value.copy(extraFolders = list)
        store.saveSettings(settings.value)
        message.value = "Pasta \"$name\" adicionada à biblioteca"
        scanLibrary()
    }

    fun removeFolder(f: ExtraFolder) {
        val list = settings.value.extraFolders.filter { it.ref != f.ref }
        settings.value = settings.value.copy(extraFolders = list)
        store.saveSettings(settings.value)
        if (f.ref.startsWith("content://")) {
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(f.ref),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        scanLibrary()
    }

    fun toggleAutoMeta(v: Boolean) {
        settings.value = settings.value.copy(autoFetchMeta = v)
        store.saveSettings(settings.value)
    }

    fun toggleAutoKindle(v: Boolean) {
        settings.value = settings.value.copy(autoOpenKindle = v)
        store.saveSettings(settings.value)
    }

    // ---------------- Kindle ----------------

    fun saveKindleUri(uri: Uri) {
        kindle.persistTree(uri)
        kindleUri.value = uri
        settings.value = settings.value.copy(kindleFolderUri = uri.toString())
        store.saveSettings(settings.value)
        message.value = "Pasta do Kindle definida"
        refreshKindle()
    }

    fun refreshKindle() {
        val uri = kindleUri.value ?: return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { kindle.listBooks(uri) }
            kindleBooks.value = list
        }
    }

    fun sendToKindle(selected: List<Book>) {
        if (selected.isEmpty()) return
        val uri = kindleUri.value
        if (uri == null) { message.value = "Escolha primeiro a pasta documents do Kindle"; return }
        viewModelScope.launch {
            transferProgress.value = 0 to selected.size
            var ok = 0
            selected.forEachIndexed { i, b ->
                val success = withContext(Dispatchers.IO) { kindle.sendToKindle(b, uri) }
                if (success) {
                    ok++
                    updateBook(b.copy(transferred = true))
                }
                transferProgress.value = (i + 1) to selected.size
            }
            transferProgress.value = null
            transferDone.value = "$ok de ${selected.size} livro(s) enviados para o Kindle"
            message.value = "$ok de ${selected.size} livro(s) enviados para o Kindle"
            refreshKindle()
        }
    }

    fun deleteFromKindle(item: KindleItem) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { kindle.deleteFromKindle(item) }
            message.value = if (ok) "\"${item.name}\" excluído do Kindle" else "Falha ao excluir do Kindle"
            refreshKindle()
        }
    }

    fun openBookFile(b: Book) {
        runCatching { kindle.openBookFile(getApplication(), b) }
            .onFailure { message.value = "Nenhum aplicativo instalado consegue abrir este formato" }
    }

    // ---------------- USB (best-effort) ----------------

    fun refreshUsb() {
        usbDevice.value = withContext2 { kindle.probeUsb(getApplication()) }
        if (usbDevice.value != null && settings.value.autoOpenKindle && screen.value == Screen.Library) {
            // não navega automaticamente para não interromper — apenas informa
        }
    }

    fun onUsbEvent(attached: Boolean) {
        viewModelScope.launch {
            delay(1200)
            refreshUsb()
        }
        if (attached && settings.value.autoOpenKindle && screen.value == Screen.Library) {
            message.value = "Dispositivo USB detectado — use a aba Kindle para transferir livros"
        }
    }

    private fun withContext2(block: () -> String?): String? = runCatching { block() }.getOrNull()

    // ---------------- util ----------------

    fun clearMessage() { message.value = null }
    fun clearTransferDone() { transferDone.value = null }

    private fun refreshCollections() {
        val map = LinkedHashMap<String, Int>()
        books.value.forEach { b -> if (b.collection.isNotBlank()) map[b.collection] = (map[b.collection] ?: 0) + 1 }
        collections.value = map.map { CollectionInfo(it.key, it.value) }.sortedBy { it.name.lowercase() }
    }
}
